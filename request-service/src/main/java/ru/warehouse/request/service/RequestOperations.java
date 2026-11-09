package ru.warehouse.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.request.entity.Request;
import ru.warehouse.request.repository.RequestRepository;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;

/**
 * S2: операции по заявке, которым нужен вызов другого сервиса, выполняются в три фазы вместо одной длинной транзакции
 * с блокировкой строки заявки:
 * <ol>
 *   <li><b>Подготовка</b> (короткая транзакция): занять аренду операции на заявке, проверить предусловия,
 *       снять данные для внешнего вызова в обычные объекты;</li>
 *   <li><b>Внешний вызов</b> без транзакции и без блокировок БД (соединение из пула не занято);</li>
 *   <li><b>Фиксация</b> (короткая транзакция): заблокировать заявку, проверить, что аренда за нами, применить результат
 *       и снять аренду.</li>
 * </ol>
 * Пока аренда занята, остальные изменяющие операции по заявке (в том числе комплектация) получают
 * 409 OPERATION_IN_PROGRESS (см. {@link #assertFree}); читать заявку можно всегда. Аренда сама истекает по TTL,
 * если процесс упал посреди операции. Внешние вызовы идемпотентны или восстанавливаемы (ключи, подхват документов),
 * поэтому повтор после сбоя безопасен.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestOperations {
    /** Фиксация результата: заявка уже заблокирована в этой транзакции. */
    @FunctionalInterface
    public interface Finish<S, E, R> {
        R apply(Request locked, S prepared, E external);
    }

    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final RequestRepository requestRepository;

    public <S, E, R> R run(UUID requestId, Duration ttl, Function<Request, S> prepare,
                           Function<S, E> external, Finish<S, E, R> finish) {
        UUID token = UUID.randomUUID();

        // 1. Подготовка. Исключение откатывает транзакцию вместе с арендой.
        S prepared = tx.execute(status -> {
            acquire(requestId, token, ttl);
            Request r = requestRepository.findById(requestId)
                    .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Заявка не найдена"));
            return prepare.apply(r);
        });

        // 2. Внешний вызов вне транзакции
        E result;
        try {
            result = external.apply(prepared);
        } catch (RuntimeException | Error e) {
            releaseQuietly(requestId, token);
            throw e;
        }

        // 3. Фиксация
        try {
            return tx.execute(status -> {
                Request r = requestRepository.findByIdForUpdate(requestId)
                        .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Заявка не найдена"));
                assertHeld(requestId, token);
                R response = finish.apply(r, prepared, result);
                release(requestId, token);
                return response;
            });
        } catch (RuntimeException | Error e) {
            releaseQuietly(requestId, token);
            throw e;
        }
    }

    /**
     * Для остальных изменяющих операций: вызывать после блокировки строки заявки. Если по заявке идёт
     * операция с внешним вызовом, отвечает 409 (клиент повторяет запрос через несколько секунд).
     */
    public void assertFree(UUID requestId) {
        Boolean busy = jdbc.queryForObject(
                "SELECT COALESCE(op_token IS NOT NULL AND op_until > clock_timestamp(), FALSE) FROM requests WHERE id = ?",
                Boolean.class, requestId);
        if (Boolean.TRUE.equals(busy)) {
            throw ApiException.conflict("OPERATION_IN_PROGRESS",
                    "По заявке выполняется другая операция: повторите через несколько секунд");
        }
    }

    private void acquire(UUID requestId, UUID token, Duration ttl) {
        int updated = jdbc.update("UPDATE requests SET op_token = ?, op_until = clock_timestamp() + make_interval(secs => ?) "
                        + "WHERE id = ? AND (op_token IS NULL OR op_until < clock_timestamp() OR op_token = ?)",
                token, (double) ttl.toSeconds(), requestId, token);
        if (updated == 1) return;
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM requests WHERE id = ?", Integer.class, requestId);
        if (exists == null || exists == 0) {
            throw ApiException.notFound("REQUEST_NOT_FOUND", "Заявка не найдена");
        }
        throw ApiException.conflict("OPERATION_IN_PROGRESS",
                "По заявке выполняется другая операция: повторите через несколько секунд");
    }

    private void assertHeld(UUID requestId, UUID token) {
        Boolean mine = jdbc.queryForObject("SELECT COALESCE(op_token = ?, FALSE) FROM requests WHERE id = ?",
                Boolean.class, token, requestId);
        if (!Boolean.TRUE.equals(mine)) {
            // аренда истекла, и заявку занял другой запрос: внешний вызов мог уже сработать, повтор подхватит результат
            throw ApiException.conflict("OPERATION_EXPIRED",
                    "Операция заняла слишком много времени и была прервана: повторите её");
        }
    }

    private void release(UUID requestId, UUID token) {
        jdbc.update("UPDATE requests SET op_token = NULL, op_until = NULL WHERE id = ? AND op_token = ?", requestId, token);
    }

    private void releaseQuietly(UUID requestId, UUID token) {
        try {
            tx.executeWithoutResult(status -> release(requestId, token));
        } catch (RuntimeException e) {
            log.warn("Не удалось снять аренду операции по заявке {}: истечёт сама по TTL", requestId, e);
        }
    }
}
