package ru.warehouse.inventory.it;

import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import ru.warehouse.common.event.OutboxRelay;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * M1: отправка из outbox. Kafka подменена заглушкой, база настоящая (PostgreSQL в контейнере):
 * порядок, «парковка» ядовитых событий, временные сбои и синхронное исключение при отправке.
 */
@SuppressWarnings("unchecked")
class OutboxRelayIntegrationTest extends IntegrationTestBase {
    @Autowired JdbcClient jdbcClient;
    @Autowired PlatformTransactionManager txManager;

    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private static final int MAX_ATTEMPTS = 3;

    private OutboxRelay relay() {
        return new OutboxRelay(jdbcClient, kafka, txManager, 100, 7, true, MAX_ATTEMPTS, Duration.ofSeconds(60));
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM outbox_event");
    }

    private void event(String key) {
        jdbc.update("INSERT INTO outbox_event (event_id, topic, event_key, payload) VALUES (?, 'test.topic', ?, CAST(? AS jsonb))",
                UUID.randomUUID(), key, "{\"k\":\"" + key + "\"}");
    }

    private boolean published(String key) {
        return jdbc.queryForObject("SELECT published_at IS NOT NULL FROM outbox_event WHERE event_key = ?", Boolean.class, key);
    }

    private int attempts(String key) {
        return jdbc.queryForObject("SELECT attempts FROM outbox_event WHERE event_key = ?", Integer.class, key);
    }

    private void sendOk(String key) {
        doReturn(CompletableFuture.completedFuture(null)).when(kafka).send(eq("test.topic"), eq(key), anyString());
    }

    @Test
    void publishesInOrder_andMarksPublished() {
        event("k1"); event("k2"); event("k3");
        sendOk("k1"); sendOk("k2"); sendOk("k3");

        relay().relay();

        InOrder order = inOrder(kafka);
        order.verify(kafka).send(eq("test.topic"), eq("k1"), anyString());
        order.verify(kafka).send(eq("test.topic"), eq("k2"), anyString());
        order.verify(kafka).send(eq("test.topic"), eq("k3"), anyString());
        assertThat(published("k1") && published("k2") && published("k3")).isTrue();
    }

    @Test
    void poisonEvent_isParkedAfterMaxAttempts_andDoesNotBlockOthers() {
        event("bad"); event("good");
        doReturn(CompletableFuture.failedFuture(new RecordTooLargeException("слишком большое")))
                .when(kafka).send(eq("test.topic"), eq("bad"), anyString());
        sendOk("good");

        OutboxRelay relay = relay();
        for (int i = 0; i < MAX_ATTEMPTS + 1; i++) relay.relay();

        assertThat(published("good")).isTrue();
        assertThat(published("bad")).isFalse();
        assertThat(attempts("bad")).isEqualTo(MAX_ATTEMPTS);
        assertThat(jdbc.queryForObject("SELECT last_error FROM outbox_event WHERE event_key = 'bad'", String.class)).isNotBlank();
        // после парковки событие больше не отправляется
        verify(kafka, times(MAX_ATTEMPTS)).send(eq("test.topic"), eq("bad"), anyString());
    }

    @Test
    void transientFailure_stopsTheBatch_withoutCountingAnAttempt() {
        event("t1"); event("t2");
        doReturn(CompletableFuture.failedFuture(new TimeoutException("брокер недоступен")))
                .when(kafka).send(eq("test.topic"), eq("t1"), anyString());
        sendOk("t2");

        relay().relay();

        assertThat(published("t1")).isFalse();
        assertThat(attempts("t1")).isZero();
        assertThat(published("t2")).isFalse(); // порядок сохраняется: следующее событие уйдёт после первого
    }

    @Test
    void synchronousSendException_isHandledAsPermanentFailure_andPriorEventsStayPublished() {
        event("s0"); event("s1"); event("s2");
        sendOk("s0");
        when(kafka.send(eq("test.topic"), eq("s1"), anyString())).thenThrow(new SerializationException("не сериализуется"));
        sendOk("s2");

        relay().relay(); // не должен бросать исключение и откатывать уже отправленное

        assertThat(published("s0")).isTrue();
        assertThat(published("s1")).isFalse();
        assertThat(attempts("s1")).isEqualTo(1);
        assertThat(published("s2")).isTrue();
    }
}
