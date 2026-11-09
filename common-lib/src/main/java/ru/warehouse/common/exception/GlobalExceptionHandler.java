package ru.warehouse.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.warehouse.common.web.TraceIdFilter;
import java.util.List;

/** Общий обработчик ошибок. LOWEST_PRECEDENCE: специализированные обработчики срабатывают раньше. */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException e) {
        ResponseEntity<ApiError> response = build(e.getStatus(), e.getCode(), e.getMessage(), e.getDetails());
        if (e.getRetryAfterSeconds() == null) {
            return response;
        }
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(response.getBody());
    }

    /** Ошибки Bean Validation: собираем «поле: сообщение». */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Некорректные данные запроса", details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> handleMalformed(Exception e) {
        // причину (поле, тип, неизвестное свойство) пишем в лог, клиенту не отдаём
        log.warn("Запрос не удалось разобрать: {}", rootMessage(e));
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Запрос не удалось разобрать", List.of());
    }

    /** Отказ method security (@PreAuthorize): без этого общий обработчик превратил бы его в 500. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleDenied(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Недостаточно прав для выполнения операции", List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuth(AuthenticationException e) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Требуется аутентификация", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleOther(Exception e) {
        // Стандартные исключения Spring (404 для неизвестного URL, 405, 400 при нехватке параметра)
        // сами несут верный HTTP-статус; сохраняем его, а не превращаем в 500
        if (e instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            return build(er.getStatusCode(), "REQUEST_ERROR", "Некорректный запрос", List.of());
        }
        log.error("Необработанная ошибка", e); // подробности только в лог, клиенту не отдаём
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Внутренняя ошибка сервера", List.of());
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        for (int i = 0; t.getCause() != null && i < 10; i++) t = t.getCause();
        String m = String.valueOf(t.getMessage());
        return m.length() > 400 ? m.substring(0, 400) + "..." : m;
    }

    static ResponseEntity<ApiError> build(HttpStatusCode status, String code, String message, List<String> details) {
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, details, MDC.get(TraceIdFilter.MDC_KEY)));
    }
}
