package ru.warehouse.common.security;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import ru.warehouse.common.web.TraceIdFilter;
import java.io.IOException;

/**
 * Пишет ошибку 401/403 в том же формате, что и обработчик контроллеров.
 * Сообщения здесь константы, а trace-id проверен фильтром, поэтому ручная сборка JSON безопасна.
 */
final class JsonErrorWriter {
    private JsonErrorWriter() {}

    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"details\":[],\"trace_id\":" + (traceId == null ? "null" : "\"" + traceId + "\"") + "}");
    }
}