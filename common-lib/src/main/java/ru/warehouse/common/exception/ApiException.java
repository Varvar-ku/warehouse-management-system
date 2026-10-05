package ru.warehouse.common.exception;

import org.springframework.http.HttpStatus;
import java.util.List;

/** Ожидаемая бизнес-ошибка: сервисный слой бросает её, обработчик превращает в ответ. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<String> details;

    private ApiException(HttpStatus status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public List<String> getDetails() { return details; }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, List.of());
    }
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message, List.of());
    }
    public static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, List.of());
    }
    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message, List.of());
    }
    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message, List.of());
    }
    /** Конфликт с перечнем причин (например, что именно мешает подтвердить документ). */
    public static ApiException conflict(String code, String message, List<String> details) {
        return new ApiException(HttpStatus.CONFLICT, code, message, details);
    }

    /** Ошибка соседнего сервиса или его недоступность (502). */
    public static ApiException upstream(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", message, List.of());
    }
}