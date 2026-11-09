package ru.warehouse.notification.service;

import org.springframework.stereotype.Component;
import ru.warehouse.notification.dto.EventEnvelope;
import ru.warehouse.notification.entity.NotificationType;

import java.util.Optional;
import java.util.UUID;

/**
 * Единственное место, где событие превращается в уведомление. Обрабатывается только действие
 * request.status_changed (его пишет request-service); все остальные события аудита игнорируются.
 */
@Component
public class RequestNotificationMapper {

    public static final String ACTION = "request.status_changed";
    /** Уведомления о заявках создаёт только событие request-service (проверка происхождения, а не защита от подделки). */
    public static final String SOURCE_SERVICE = "request-service";

    public Optional<NotificationDraft> map(EventEnvelope event) {
        EventEnvelope.Details d = event.details();
        if (!ACTION.equals(event.action()) || !SOURCE_SERVICE.equals(event.service()) || d == null
                || d.requesterId() == null || isBlank(d.newStatus())) {
            return Optional.empty();
        }
        UUID requestId = parseUuid(event.entityId());
        String ref = requestLabel(d.requestNumber(), requestId);
        UUID to = d.requesterId();
        return switch (d.newStatus()) {
            case "APPROVED" -> draft(to, requestId, NotificationType.REQUEST_APPROVED,
                    "Заявка принята", "Заявка " + ref + " принята кладовщиком.");
            case "PARTIALLY_AVAILABLE" -> draft(to, requestId, NotificationType.REQUEST_PARTIALLY_AVAILABLE,
                    "Заявка принята частично", "Заявка " + ref + " принята: часть позиций сейчас недоступна.");
            case "RESERVED" -> draft(to, requestId, NotificationType.REQUEST_RESERVED,
                    "Товар зарезервирован", "Для заявки " + ref + " товар зарезервирован полностью.");
            case "RESERVED_PARTIAL" -> draft(to, requestId, NotificationType.REQUEST_RESERVED_PARTIAL,
                    "Товар зарезервирован частично", "Для заявки " + ref + " зарезервирована только часть товара.");
            case "READY_FOR_ISSUE" -> draft(to, requestId, NotificationType.REQUEST_READY_FOR_ISSUE,
                    "Заявка готова к выдаче", "Заявка " + ref + " собрана и готова к выдаче.");
            case "ISSUED" -> draft(to, requestId, NotificationType.REQUEST_ISSUED,
                    "Заявка выдана", "Товар по заявке " + ref + " выдан.");
            case "REJECTED" -> draft(to, requestId, NotificationType.REQUEST_REJECTED,
                    "Заявка отклонена", "Заявка " + ref + " отклонена кладовщиком. Причина указана в карточке заявки.");
            case "CANCELLED" -> to.equals(d.actorId()) ? Optional.empty() : draft(to, requestId, NotificationType.REQUEST_CANCELLED,
                    "Заявка отменена", "Заявка " + ref + " отменена.");
            default -> Optional.empty();
        };
    }

    private Optional<NotificationDraft> draft(UUID recipient, UUID requestId, NotificationType type,
                                              String title, String message) {
        return Optional.of(new NotificationDraft(recipient, type, title, message, requestId));
    }

    private String requestLabel(String number, UUID requestId) {
        if (!isBlank(number)) {
            return "№ " + number;
        }
        return requestId != null ? "(" + requestId.toString().substring(0, 8) + ")" : "";
    }

    private UUID parseUuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
