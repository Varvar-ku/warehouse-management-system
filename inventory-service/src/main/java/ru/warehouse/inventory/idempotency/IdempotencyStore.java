// idempotency/IdempotencyStore.java
package ru.warehouse.inventory.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Хранилище ключей идемпотентности (JDBC: нужна атомарная вставка INSERT ... ON CONFLICT). */
@Component
@RequiredArgsConstructor
class IdempotencyStore {
    private final JdbcTemplate jdbc;

    record Entry(String fingerprint, String status, Integer responseStatus, String responseBody,
                 OffsetDateTime createdAt) {}

    /** true, если ключ занят нами (первый запрос с этим ключом). owner: токен именно этой попытки. */
    boolean claim(UUID userId, String key, String fingerprint, UUID owner) {
        return jdbc.update("INSERT INTO idempotency_keys (user_id, idempotency_key, fingerprint, status, owner) "
                        + "VALUES (?, ?, ?, 'IN_PROGRESS', ?) ON CONFLICT DO NOTHING",
                userId, key, fingerprint, owner) == 1;
    }

    Optional<Entry> find(UUID userId, String key) {
        return jdbc.query("SELECT fingerprint, status, response_status, response_body, created_at "
                        + "FROM idempotency_keys WHERE user_id = ? AND idempotency_key = ?",
                (rs, n) -> new Entry(rs.getString("fingerprint"), rs.getString("status"),
                        (Integer) rs.getObject("response_status"), rs.getString("response_body"),
                        rs.getObject("created_at", OffsetDateTime.class)),
                userId, key).stream().findFirst();
    }

    /** Перехват «зависшего» запроса: UPDATE атомарен, выполнится только у одного претендента; владельцем становится он. */
    boolean takeOver(UUID userId, String key, Duration timeout, UUID newOwner) {
        return jdbc.update("UPDATE idempotency_keys SET owner = ?, created_at = CURRENT_TIMESTAMP "
                        + "WHERE user_id = ? AND idempotency_key = ? AND status = 'IN_PROGRESS' "
                        + "AND created_at < CURRENT_TIMESTAMP - make_interval(secs => ?)",
                newOwner, userId, key, (double) timeout.toSeconds()) == 1;
    }

    /**
     * «Сердцебиение»: пока запрос выполняется, продлеваем запись. created_at здесь служит отметкой последней
     * активности (её же обновляет takeOver), поэтому схема таблицы не меняется.
     */
    boolean touch(UUID userId, String key, UUID owner) {
        return jdbc.update("UPDATE idempotency_keys SET created_at = CURRENT_TIMESTAMP "
                        + "WHERE user_id = ? AND idempotency_key = ? AND owner = ? AND status = 'IN_PROGRESS'",
                userId, key, owner) == 1;
    }

    /** Завершает запись только если мы всё ещё её владелец. false: ключ перехвачен, наш результат не сохраняем. */
    boolean complete(UUID userId, String key, UUID owner, int status, String body) {
        return jdbc.update("UPDATE idempotency_keys SET status = 'COMPLETED', response_status = ?, response_body = ?, "
                        + "completed_at = CURRENT_TIMESTAMP "
                        + "WHERE user_id = ? AND idempotency_key = ? AND owner = ? AND status = 'IN_PROGRESS'",
                status, body, userId, key, owner) == 1;
    }

    /** Освобождает ключ только если он наш и не завершён: чужую или завершённую запись не затрагивает. */
    void release(UUID userId, String key, UUID owner) {
        jdbc.update("DELETE FROM idempotency_keys "
                        + "WHERE user_id = ? AND idempotency_key = ? AND owner = ? AND status = 'IN_PROGRESS'",
                userId, key, owner);
    }
    int cleanup(Duration retention) {
        return jdbc.update("DELETE FROM idempotency_keys WHERE created_at < CURRENT_TIMESTAMP - make_interval(secs => ?)",
                (double) retention.toSeconds());
    }
}