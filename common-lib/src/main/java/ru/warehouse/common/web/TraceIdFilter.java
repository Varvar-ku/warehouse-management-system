package ru.warehouse.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Берёт X-Trace-Id из запроса или создаёт новый, кладёт в MDC (попадает в логи и в trace_id ошибок)
 * и возвращает в ответе. Идентификатор подставляется и в заголовок запроса, поэтому gateway
 * передаёт его дальше сервисам. Выполняется РАНЬШЕ Spring Security, чтобы 401/403 тоже имели trace_id.
 */
public class TraceIdFilter extends OncePerRequestFilter implements Ordered {
    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    // Принимаем только безопасные значения: идентификатор попадает в логи и JSON ответа
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String traceId = (incoming != null && VALID.matcher(incoming).matches())
                ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(new TraceRequest(request, traceId), response);
        } finally {
            MDC.remove(MDC_KEY); // потоки переиспользуются, обязательно чистим
        }
    }

    /** Гарантирует, что заголовок X-Trace-Id есть в запросе (нужно для проксирования в gateway). */
    private static final class TraceRequest extends HttpServletRequestWrapper {
        private final String traceId;

        TraceRequest(HttpServletRequest request, String traceId) {
            super(request);
            this.traceId = traceId;
        }

        @Override
        public String getHeader(String name) {
            return HEADER.equalsIgnoreCase(name) ? traceId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HEADER.equalsIgnoreCase(name) ? Collections.enumeration(List.of(traceId)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            names.addAll(Collections.list(super.getHeaderNames()));
            names.add(HEADER);
            return Collections.enumeration(names);
        }
    }
}