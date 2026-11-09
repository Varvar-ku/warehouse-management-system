package ru.warehouse.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import ru.warehouse.common.event.OutboxPublisher;
import java.util.UUID;

/**
 * S4: записи аудита warehouse-service уходят в неизменяемый журнал через outbox (в той же транзакции, что и
 * изменение), а не строкой лога. Исполнитель берётся из JWT текущего запроса. Вызывать только внутри транзакции.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class WarehouseAudit {
    private static final String SERVICE = "warehouse-service";
    private final ObjectProvider<OutboxPublisher> outbox;

    /** details: пары «ключ, значение». */
    void record(String action, String entityType, UUID entityId, Object... details) {
        OutboxPublisher publisher = outbox.getIfAvailable();
        if (publisher == null) {
            log.error("OutboxPublisher не подключён: событие аудита {} ({} {}) не записано", action, entityType, entityId);
            return;
        }
        publisher.audit(SERVICE, action, entityType, entityId, currentActor(), OutboxPublisher.details(details));
    }

    private static UUID currentActor() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            try {
                return UUID.fromString(jwt.getToken().getSubject());
            } catch (IllegalArgumentException | NullPointerException ex) {
                return null;
            }
        }
        return null;
    }
}
