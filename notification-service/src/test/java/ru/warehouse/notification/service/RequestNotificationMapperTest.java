package ru.warehouse.notification.service;

import org.junit.jupiter.api.Test;
import ru.warehouse.notification.dto.EventEnvelope;
import ru.warehouse.notification.entity.NotificationType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestNotificationMapperTest {

    private final RequestNotificationMapper mapper = new RequestNotificationMapper();

    private EventEnvelope event(String action, String status, UUID requesterId) {
        var details = new EventEnvelope.Details("42", requesterId, "UNDER_REVIEW", status, null); // actorId не важен для уведомления
        return new EventEnvelope(UUID.randomUUID(), "request-service", action, "REQUEST",
                UUID.randomUUID().toString(), "trace-1", details);
    }

    @Test
    void mapsStatusChangeToNotificationForRequester() {
        UUID requester = UUID.randomUUID();
        var draft = mapper.map(event("request.status_changed", "RESERVED", requester));

        assertThat(draft).isPresent();
        assertThat(draft.get().type()).isEqualTo(NotificationType.REQUEST_RESERVED);
        assertThat(draft.get().recipientId()).isEqualTo(requester);
        assertThat(draft.get().message()).contains("№ 42");
    }

    @Test
    void ignoresOtherActionsUnrelatedStatusesAndMissingRecipient() {
        assertThat(mapper.map(event("reservation.created", "RESERVED", UUID.randomUUID()))).isEmpty();
        assertThat(mapper.map(event("request.status_changed", "PICKING", UUID.randomUUID()))).isEmpty();
        assertThat(mapper.map(event("request.status_changed", "RESERVED", null))).isEmpty();
    }
}