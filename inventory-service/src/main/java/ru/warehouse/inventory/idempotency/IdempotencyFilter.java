package ru.warehouse.inventory.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.json.JsonMapper;
import ru.warehouse.common.web.TraceIdFilter;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Защита от двойного выполнения (разд. 18 ТЗ, M3) для POST/PUT/PATCH/DELETE с заголовком Idempotency-Key:
 *  - первый запрос выполняется, 2xx-ответ сохраняется;
 *  - повтор с тем же ключом и тем же запросом возвращает сохранённый ответ без повторного выполнения;
 *  - тот же ключ с другим запросом: 422; пока первый ещё выполняется: 409.
 * Работает после Spring Security: ключ принадлежит пользователю из JWT.
 * Отличия от первой версии:
 *  - пока запрос выполняется, запись «сердцебиением» продлевается, поэтому долгий запрос не будет перехвачен
 *    и выполнен параллельно; перехватывается только брошенная запись (процесс упал);
 *  - отпечаток считается по каноническому JSON (порядок полей и форма записи чисел не важны);
 *  - если не удалось сохранить ответ уже выполненного запроса, ключ не освобождается (повтор не выполнится сразу).
 * Ограничение: бизнес-транзакция и сохранение ответа это разные транзакции. Если процесс упадёт между ними,
 * после таймаута запрос выполнится повторно; от дублей защищают проверки статусов в самих операциях.
 */
class IdempotencyFilter extends OncePerRequestFilter implements Ordered {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final Set<String> METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Pattern VALID_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,100}$");
    private static final int MAX_BODY = 1_000_000;

    private final IdempotencyStore store;
    private final IdempotencyProperties props;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "idempotency-heartbeat");
        t.setDaemon(true);
        return t;
    });
    private final JsonMapper mapper;
    private final AntPathMatcher matcher = new AntPathMatcher();

    IdempotencyFilter(IdempotencyStore store, IdempotencyProperties props, JsonMapper mapper) {
        this.store = store;
        this.props = props;
        this.mapper = mapper;
    }

    /** Исполнитель «сердцебиения» принадлежит фильтру и не публикуется бином: иначе Spring использовал бы его для @Scheduled. */
    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader("Idempotency-Key");
        boolean required = props.requiredPaths().stream()
                .anyMatch(p -> matcher.match(p, request.getRequestURI()));
        if (key == null || key.isBlank()) {
            if (required) {
                error(response, 400, "IDEMPOTENCY_KEY_REQUIRED", "Для этой операции обязателен заголовок Idempotency-Key");
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        if (!VALID_KEY.matcher(key).matches()) {
            error(response, 400, "IDEMPOTENCY_KEY_INVALID", "Idempotency-Key: 8-100 символов (буквы, цифры, . _ : -)");
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt)) {
            chain.doFilter(request, response);
            return;
        }
        UUID userId = UUID.fromString(jwt.getToken().getSubject());

        byte[] body = request.getInputStream().readAllBytes();
        if (body.length > MAX_BODY) {
            error(response, 413, "PAYLOAD_TOO_LARGE", "Тело запроса слишком большое");
            return;
        }
        String fingerprint = fingerprint(request, body);

        UUID owner = UUID.randomUUID(); // токен этой попытки
        if (!store.claim(userId, key, fingerprint, owner)) {
            var existing = store.find(userId, key).orElse(null);
            if (existing == null) {
                // запись исчезла между claim и find: одна повторная попытка занять ключ
                if (!store.claim(userId, key, fingerprint, owner)) {
                    error(response, 409, "REQUEST_IN_PROGRESS", "Запрос с этим ключом ещё выполняется");
                    return;
                }
            } else {
                if (!existing.fingerprint().equals(fingerprint)) {
                    error(response, 422, "IDEMPOTENCY_KEY_REUSED", "Этот Idempotency-Key уже использован для другого запроса");
                    return;
                }
                if ("COMPLETED".equals(existing.status())) {
                    replay(response, existing.responseStatus(), existing.responseBody());
                    return;
                }
                // IN_PROGRESS: перехватываем только запись без «сердцебиения» дольше таймаута
                if (!store.takeOver(userId, key, props.inProgressTimeout(), owner)) {
                    error(response, 409, "REQUEST_IN_PROGRESS", "Запрос с этим ключом ещё выполняется");
                    return;
                }
                log.warn("Перехвачен брошенный ключ идемпотентности {} пользователя {}", key, userId);
            }
        }

        long beatMillis = Math.max(1000, props.inProgressTimeout().toMillis() / 3);
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            try {
                store.touch(userId, key, owner);
            } catch (RuntimeException e) {
                log.warn("Не удалось продлить ключ идемпотентности {}: {}", key, e.getMessage());
            }
        }, beatMillis, beatMillis, TimeUnit.MILLISECONDS);

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        boolean keep = false; // true: запрос выполнен успешно, запись нельзя освобождать
        try {
            chain.doFilter(new CachedBodyRequest(request, body), wrapped);
            int status = wrapped.getStatus();
            if (status >= 200 && status < 300) {
                keep = true;
                try {
                    boolean saved = store.complete(userId, key, owner, status,
                            new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8));
                    if (!saved) {
                        log.warn("Ключ идемпотентности {} перехвачен другой попыткой, ответ не сохранён", key);
                    }
                } catch (RuntimeException e) {
                    // запрос уже выполнен: ответ уходит клиенту, ключ остаётся IN_PROGRESS до таймаута
                    log.error("Не удалось сохранить ответ по ключу идемпотентности {}", key, e);
                }
            }
        } finally {
            heartbeat.cancel(false);
            if (!keep) store.release(userId, key, owner); // ошибка или не-2xx: освобождаем ТОЛЬКО свою запись
            wrapped.copyBodyToResponse();
        }
    }

    // ---------- отпечаток запроса ----------

    private String fingerprint(HttpServletRequest request, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((request.getMethod() + " " + request.getRequestURI() + "?"
                    + (request.getQueryString() == null ? "" : request.getQueryString()) + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            digest.update(canonicalBody(body));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** JSON приводится к каноническому виду: поля по алфавиту, числа без лишних нулей. Не JSON: сырые байты. */
    private byte[] canonicalBody(byte[] body) {
        if (body.length == 0) return body;
        try {
            Object tree = mapper.readValue(body, Object.class);
            StringBuilder sb = new StringBuilder();
            writeCanonical(sb, tree);
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return body;
        }
    }

    private void writeCanonical(StringBuilder sb, Object node) {
        if (node == null) {
            sb.append("null");
        } else if (node instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), v));
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(mapper.writeValueAsString(e.getKey())).append(':');
                writeCanonical(sb, e.getValue());
            }
            sb.append('}');
        } else if (node instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                writeCanonical(sb, list.get(i));
            }
            sb.append(']');
        } else if (node instanceof Number n) {
            BigDecimal d = new BigDecimal(n.toString()).stripTrailingZeros();
            sb.append(d.signum() == 0 ? "0" : d.toPlainString());
        } else if (node instanceof Boolean b) {
            sb.append(b);
        } else {
            sb.append(mapper.writeValueAsString(String.valueOf(node)));
        }
    }

    // ---------- ответы ----------

    private static void replay(HttpServletResponse response, Integer status, String body) throws IOException {
        response.setStatus(status == null ? 200 : status);
        response.setHeader("Idempotency-Replayed", "true");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        if (body != null && !body.isEmpty()) response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    /** Ошибка в формате ТЗ; сообщения здесь константы, а trace-id проверен TraceIdFilter. */
    private static void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"details\":[],\"trace_id\":" + (traceId == null ? "null" : "\"" + traceId + "\"") + "}");
    }

    /** Запрос с уже прочитанным телом: фильтр прочитал его для отпечатка, контроллеру нужно прочитать снова. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset cs = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), cs));
        }
    }
}
