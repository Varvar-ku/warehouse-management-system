package ru.warehouse.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import ru.warehouse.common.event.OutboxPublisher;
import java.util.UUID;

/**
 * S4: записи аудита уходят в неизменяемый журнал через outbox в той же транзакции, что и изменение.
 * Вызывать только внутри транзакции. details: пары «ключ, значение»; персональные данные и пароли не передавать.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AuthAudit {
    private static final String SERVICE = "auth-service";
    private final ObjectProvider<OutboxPublisher> outbox;

    void record(String action, String entityType, Object entityId, UUID actorId, Object... details) {
        OutboxPublisher publisher = outbox.getIfAvailable();
        if (publisher == null) {
            log.error("OutboxPublisher не подключён: событие аудита {} ({} {}) не записано", action, entityType, entityId);
            return;
        }
        publisher.audit(SERVICE, action, entityType, entityId, actorId, OutboxPublisher.details(details));
    }
}
