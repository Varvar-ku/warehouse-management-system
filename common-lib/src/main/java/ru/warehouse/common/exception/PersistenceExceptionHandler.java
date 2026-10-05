package ru.warehouse.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.List;

/**
 * Ошибки БД. Вынесены в отдельный класс: он подключается только при наличии JPA
 * (в gateway БД нет). HIGHEST_PRECEDENCE: должен сработать раньше общего Exception-обработчика.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PersistenceExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(PersistenceExceptionHandler.class);

    /** Последняя линия защиты от гонок: например, два одновременных POST с одним SKU или логином. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("Нарушение ограничения БД", e);
        return GlobalExceptionHandler.build(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "Данные конфликтуют с существующими записями", List.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleLocking(ObjectOptimisticLockingFailureException e) {
        return GlobalExceptionHandler.build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "Запись была изменена другим пользователем, обновите данные", List.of());
    }
}