package ru.warehouse.common.event;

import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Отправляет неопубликованные строки outbox в Kafka.
 * <p>
 * Гарантии (M1):
 * <ul>
 *   <li><b>Порядок.</b> Выбираются только строки завершённых транзакций (<code>tx_id &lt; xmin</code> снимка), порядок
 *       <code>(tx_id, id)</code>: событие с меньшим id не может появиться позже уже отправленного с большим.
 *       Отключается {@code wms.outbox.strict-order=false}.</li>
 *   <li><b>Один отправитель.</b> Прогон берёт транзакционную advisory-блокировку: при нескольких экземплярах сервиса
 *       отправляет только один (иначе SKIP LOCKED перемешивал бы порядок).</li>
 *   <li><b>Короткая транзакция.</b> Вся пачка отправляется конвейером (сначала send для всех, потом ожидание
 *       подтверждений за общий срок 5 с), а не по одному сообщению с ожиданием.</li>
 *   <li><b>«Ядовитые» события.</b> Если Kafka отвергает событие навсегда (слишком большое, не сериализуется),
 *       оно получает счётчик попыток и после {@code max-attempts} «паркуется» (не блокирует остальные, в лог ERROR).
 *       Временные сбои (брокер недоступен, таймаут) счётчик не увеличивают: отправка просто повторится.</li>
 *   <li><b>Мониторинг.</b> Раз в минуту в лог пишется предупреждение, если очередь не двигается дольше
 *       {@code stuck-warn-seconds} или есть запаркованные события.</li>
 * </ul>
 * Доставка «минимум один раз»: сбой между отправкой и UPDATE даёт дубль, потребители отбрасывают его по event_id.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    /** Ключ advisory-блокировки «активный отправитель» (она действует в пределах БД сервиса). */
    private static final long LEADER_LOCK_KEY = 7_315_001L;
    private static final long SEND_TIMEOUT_MS = 5_000;

    private record Row(long id, String topic, String key, String payload) {
    }

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final int retentionDays;
    private final boolean strictOrder;
    private final int maxAttempts;
    private final Duration stuckWarnAfter;

    public OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, PlatformTransactionManager txManager,
                       int batchSize, int retentionDays, boolean strictOrder, int maxAttempts,
                       Duration stuckWarnAfter) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.tx = new TransactionTemplate(txManager);
        this.batchSize = batchSize;
        this.retentionDays = retentionDays;
        this.strictOrder = strictOrder;
        this.maxAttempts = maxAttempts;
        this.stuckWarnAfter = stuckWarnAfter;
    }

    @Scheduled(fixedDelayString = "${wms.outbox.poll-interval-ms:1000}")
    public void relay() {
        try {
            tx.executeWithoutResult(status -> relayBatch());
        } catch (RuntimeException ex) {
            log.warn("Outbox relay iteration failed: {}", ex.toString());
        }
    }

    private void relayBatch() {
        Boolean leader = jdbc.sql("SELECT pg_try_advisory_xact_lock(:key)")
                .param("key", LEADER_LOCK_KEY).query(Boolean.class).single();
        if (!Boolean.TRUE.equals(leader)) {
            return; // другой экземпляр уже отправляет
        }
        String ready = strictOrder ? "AND tx_id < pg_snapshot_xmin(pg_current_snapshot())" : "";
        String order = strictOrder ? "tx_id, id" : "id";
        List<Row> rows = jdbc.sql("""
                        SELECT id, topic, event_key, payload::text AS payload
                        FROM outbox_event
                        WHERE published_at IS NULL AND attempts < :maxAttempts %s
                        ORDER BY %s
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED
                        """.formatted(ready, order))
                .param("maxAttempts", maxAttempts)
                .param("limit", batchSize)
                .query((rs, i) -> new Row(rs.getLong("id"), rs.getString("topic"),
                        rs.getString("event_key"), rs.getString("payload")))
                .list();
        if (rows.isEmpty()) {
            return;
        }

        // Конвейер: сначала отправляем всё, потом ждём подтверждения (транзакция держится около одного круга до брокера)
        List<CompletableFuture<?>> futures = new ArrayList<>(rows.size());
        for (Row row : rows) {
            futures.add(send(row));
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SEND_TIMEOUT_MS);
        List<Long> published = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            long leftMs = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            try {
                futures.get(i).get(leftMs, TimeUnit.MILLISECONDS);
                published.add(row.id());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException ex) {
                if (isPermanent(ex)) {
                    recordPermanentFailure(row, ex);
                    continue; // не блокируем остальные события
                }
                log.warn("Outbox send failed (id={}), will retry: {}", row.id(), ex.toString());
                break; // временный сбой: остальное отправится в следующий прогон, порядок сохраняется
            } catch (TimeoutException ex) {
                log.warn("Outbox send timed out (id={}), will retry", row.id());
                break;
            }
        }
        if (!published.isEmpty()) {
            jdbc.sql("UPDATE outbox_event SET published_at = now() WHERE id IN (:ids)")
                    .param("ids", published)
                    .update();
        }
    }

    /**
     * KafkaProducer.send может бросить исключение синхронно (например, SerializationException). Оно превращается в
     * неудачный результат строки: иначе вся транзакция откатилась бы, а уже отправленные события ушли бы повторно.
     */
    private CompletableFuture<?> send(Row row) {
        try {
            return kafka.send(row.topic(), row.key(), row.payload());
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    private void recordPermanentFailure(Row row, Throwable error) {
        String message = String.valueOf(rootMessage(error));
        if (message.length() > 500) message = message.substring(0, 500);
        Integer attempts = jdbc.sql("""
                        UPDATE outbox_event SET attempts = attempts + 1, last_error = :error
                        WHERE id = :id RETURNING attempts
                        """)
                .param("error", message).param("id", row.id())
                .query(Integer.class).single();
        if (attempts != null && attempts >= maxAttempts) {
            log.error("Outbox: событие id={} (topic {}) запарковано после {} неудачных отправок: {}",
                    row.id(), row.topic(), attempts, message);
        } else {
            log.warn("Outbox: событие id={} отвергнуто брокером (попытка {} из {}): {}",
                    row.id(), attempts, maxAttempts, message);
        }
    }

    /** Постоянная ошибка: повтор не поможет (размер, сериализация, неверное имя топика). Временные (RetriableException) нет. */
    static boolean isPermanent(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof RetriableException) return false;
            if (t instanceof RecordTooLargeException || t instanceof SerializationException
                    || t instanceof InvalidTopicException) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    /** Раз в минуту: предупреждение о застрявшей очереди и запаркованных событиях. */
    @Scheduled(fixedDelayString = "${wms.outbox.monitor-interval-ms:60000}", initialDelayString = "PT2M")
    public void monitor() {
        try {
            jdbc.sql("""
                            SELECT count(*) FILTER (WHERE attempts < :max) AS pending,
                                   count(*) FILTER (WHERE attempts >= :max) AS parked,
                                   COALESCE(EXTRACT(EPOCH FROM now() - min(created_at) FILTER (WHERE attempts < :max)), 0) AS oldest
                            FROM outbox_event WHERE published_at IS NULL
                            """)
                    .param("max", maxAttempts)
                    .query((rs, i) -> {
                        long pending = rs.getLong("pending");
                        long parked = rs.getLong("parked");
                        long oldest = rs.getLong("oldest");
                        if (parked > 0) {
                            log.error("Outbox: запаркованных событий {} (см. колонку last_error в outbox_event)", parked);
                        }
                        if (pending > 0 && oldest > stuckWarnAfter.toSeconds()) {
                            log.warn("Outbox: очередь не двигается: {} событий, старейшему {} с. Проверьте Kafka и "
                                            + "долгие незавершённые транзакции в БД (при strict-order они задерживают отправку)",
                                    pending, oldest);
                        }
                        return null;
                    }).optional();
        } catch (RuntimeException ex) {
            log.warn("Outbox monitor failed: {}", ex.toString());
        }
    }

    @Scheduled(cron = "${wms.outbox.cleanup-cron:0 0 3 * * *}")
    public void cleanup() {
        int deleted = jdbc.sql("""
                        DELETE FROM outbox_event
                        WHERE published_at IS NOT NULL
                          AND published_at < now() - make_interval(days => :days)
                        """)
                .param("days", retentionDays)
                .update();
        if (deleted > 0) {
            log.info("Outbox cleanup removed {} published rows", deleted);
        }
    }
}
