// idempotency/IdempotencyFilter.java
package ru.warehouse.inventory.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import ru.warehouse.common.web.TraceIdFilter;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Защита от двойного проведения (разд. 18 ТЗ). Для POST/PUT/PATCH/DELETE с заголовком Idempotency-Key:
 *  - первый запрос выполняется, 2xx-ответ сохраняется;
 *  - повтор с тем же ключом и тем же телом возвращает сохранённый ответ без повторного выполнения;
 *  - тот же ключ с другим запросом: 422; пока первый ещё выполняется: 409.
 * Работает ПОСЛЕ Spring Security (порядок 0 > -100): ключ принадлежит пользователю из JWT.
 */
class IdempotencyFilter extends OncePerRequestFilter implements Ordered {
    private static final Set<String> METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Pattern VALID_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,100}$");
    private static final int MAX_BODY = 1_000_000;

    private final IdempotencyStore store;
    private final IdempotencyProperties props;
    private final AntPathMatcher matcher = new AntPathMatcher();

    IdempotencyFilter(IdempotencyStore store, IdempotencyProperties props) {
        this.store = store;
        this.props = props;
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

        if (!store.claim(userId, key, fingerprint)) {
            var existing = store.find(userId, key).orElse(null);
            if (existing == null && !store.claim(userId, key, fingerprint)) {
                error(response, 409, "REQUEST_IN_PROGRESS", "Запрос с этим ключом ещё выполняется");
                return;
            }
            if (existing != null) {
                if (!existing.fingerprint().equals(fingerprint)) {
                    error(response, 422, "IDEMPOTENCY_KEY_REUSED", "Этот Idempotency-Key уже использован для другого запроса");
                    return;
                }
                if ("COMPLETED".equals(existing.status())) {
                    replay(response, existing.responseStatus(), existing.responseBody());
                    return;
                }
                // IN_PROGRESS: ждём, а если запрос «завис» дольше таймаута, перехватываем ключ
                if (!store.takeOver(userId, key, props.inProgressTimeout())) {
                    error(response, 409, "REQUEST_IN_PROGRESS", "Запрос с этим ключом ещё выполняется");
                    return;
                }
            }
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            chain.doFilter(new CachedBodyRequest(request, body), wrapped);
            int status = wrapped.getStatus();
            if (status >= 200 && status < 300) {
                store.complete(userId, key, status, new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8));
                stored = true;
            }
        } finally {
            if (!stored) store.release(userId, key); // ошибка или не-2xx: клиент вправе повторить с тем же ключом
            wrapped.copyBodyToResponse();
        }
    }

    private static String fingerprint(HttpServletRequest request, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((request.getMethod() + " " + request.getRequestURI() + "?"
                    + (request.getQueryString() == null ? "" : request.getQueryString()) + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

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

    /** Запрос с уже прочитанным телом: фильтр прочитал его для подсчёта отпечатка, контроллеру нужно прочитать снова. */
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
            java.nio.charset.Charset cs = encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), cs));
        }
    }
}