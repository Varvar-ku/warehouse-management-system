package ru.warehouse.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.sql.SQLException;
import java.util.List;

/**
 * Ошибки БД. Вынесены в отдельный класс: он подключается только при наличии JPA
 * (в gateway БД нет). HIGHEST_PRECEDENCE: должен сработать раньше общего Exception-обработчика.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PersistenceExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(PersistenceExceptionHandler.class);

    /**
     * Последняя линия защиты от гонок. M10: тип нарушения определяется по SQLSTATE PostgreSQL, а не «всегда 409»:
     * 23505 уникальность и 23503 внешний ключ: 409; 23514 нарушение инварианта учёта: 409;
     * 22001, 22003, 22P02 слишком длинное или некорректное значение: 400; 23502 NOT NULL: ошибка сервера (баг, а не
     * конфликт данных), остальное: 409 как прежде. Имена ограничений клиенту не отдаются.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException e) {
        String state = sqlState(e);
        switch (state == null ? "" : state) {
            case "23505":
                log.warn("Нарушение уникальности (SQLSTATE 23505)", e);
                return GlobalExceptionHandler.build(HttpStatus.CONFLICT, "DATA_CONFLICT",
                        "Данные конфликтуют с существующими записями", List.of());
            case "23503":
                log.warn("Нарушение внешнего ключа (SQLSTATE 23503)", e);
                return GlobalExceptionHandler.build(HttpStatus.CONFLICT, "REFERENCE_CONFLICT",
                        "Запись связана с другими данными или ссылается на несуществующую запись", List.of());
            case "23514":
                log.warn("Нарушение ограничения учёта (SQLSTATE 23514)", e);
                return GlobalExceptionHandler.build(HttpStatus.CONFLICT, "CONSTRAINT_VIOLATION",
                        "Операция нарушает ограничение учёта (например, остаток или резерв вне допустимых пределов)",
                        List.of());
            case "22001":
            case "22003":
            case "22P02":
                log.warn("Некорректное значение (SQLSTATE {})", state, e);
                return GlobalExceptionHandler.build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Значение слишком длинное, выходит за допустимый диапазон или имеет неверный формат",
                        List.of());
            case "23502":
                log.error("Обязательное поле не заполнено (SQLSTATE 23502): ошибка в коде", e);
                return GlobalExceptionHandler.build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "Внутренняя ошибка сервера", List.of());
            default:
                log.warn("Нарушение ограничения БД (SQLSTATE {})", state, e);
                return GlobalExceptionHandler.build(HttpStatus.CONFLICT, "DATA_CONFLICT",
                        "Данные конфликтуют с существующими записями", List.of());
        }
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleLocking(ObjectOptimisticLockingFailureException e) {
        return GlobalExceptionHandler.build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "Запись была изменена другим пользователем, обновите данные", List.of());
    }

    /**
     * M10: таймаут блокировки и взаимная блокировка (в том числе DeadlockLoserDataAccessException и
     * CannotAcquireLockException). Транзакция откачена целиком, повтор безопасен: отдаём 409 с Retry-After, а не 500.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleLockFailure(PessimisticLockingFailureException e) {
        log.warn("Блокировка не получена или взаимная блокировка: {}", e.getMessage());
        ResponseEntity<ApiError> response = GlobalExceptionHandler.build(HttpStatus.CONFLICT, "RESOURCE_BUSY",
                "Объект занят другой операцией, повторите запрос через секунду", List.of());
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(response.getBody());
    }

    /** SQLSTATE первой SQLException в цепочке причин (с защитой от циклов). */
    private static String sqlState(Throwable e) {
        Throwable t = e;
        for (int i = 0; t != null && i < 10; i++, t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
