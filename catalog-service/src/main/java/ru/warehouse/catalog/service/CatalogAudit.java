package ru.warehouse.catalog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
class CatalogAudit {
    private static final String SERVICE = "catalog-service";
    private final ObjectProvider<OutboxPublisher> outbox;

    void record(String action, String entityType, Object entityId, Object... details) {
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
