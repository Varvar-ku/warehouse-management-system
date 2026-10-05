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

    /** true, если ключ занят нами (первый запрос с этим ключом). */
    boolean claim(UUID userId, String key, String fingerprint) {
        return jdbc.update("INSERT INTO idempotency_keys (user_id, idempotency_key, fingerprint, status) "
                + "VALUES (?, ?, ?, 'IN_PROGRESS') ON CONFLICT DO NOTHING", userId, key, fingerprint) == 1;
    }

    Optional<Entry> find(UUID userId, String key) {
        return jdbc.query("SELECT fingerprint, status, response_status, response_body, created_at "
                        + "FROM idempotency_keys WHERE user_id = ? AND idempotency_key = ?",
                (rs, n) -> new Entry(rs.getString("fingerprint"), rs.getString("status"),
                        (Integer) rs.getObject("response_status"), rs.getString("response_body"),
                        rs.getObject("created_at", OffsetDateTime.class)),
                userId, key).stream().findFirst();
    }

    /** Перехват «зависшего» запроса (процесс упал, не сохранив результат). */
    boolean takeOver(UUID userId, String key, Duration timeout) {
        return jdbc.update("UPDATE idempotency_keys SET created_at = CURRENT_TIMESTAMP "
                        + "WHERE user_id = ? AND idempotency_key = ? AND status = 'IN_PROGRESS' "
                        + "AND created_at < CURRENT_TIMESTAMP - make_interval(secs => ?)",
                userId, key, (double) timeout.toSeconds()) == 1;
    }

    void complete(UUID userId, String key, int status, String body) {
        jdbc.update("UPDATE idempotency_keys SET status = 'COMPLETED', response_status = ?, response_body = ?, "
                + "completed_at = CURRENT_TIMESTAMP WHERE user_id = ? AND idempotency_key = ?", status, body, userId, key);
    }

    /** Освобождает ключ, если запрос не завершился успехом: клиент сможет повторить. */
    void release(UUID userId, String key) {
        jdbc.update("DELETE FROM idempotency_keys WHERE user_id = ? AND idempotency_key = ?", userId, key);
    }

    int cleanup(Duration retention) {
        return jdbc.update("DELETE FROM idempotency_keys WHERE created_at < CURRENT_TIMESTAMP - make_interval(secs => ?)",
                (double) retention.toSeconds());
    }
}