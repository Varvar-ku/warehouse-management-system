package ru.warehouse.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import ru.warehouse.notification.dto.EventEnvelope;
import ru.warehouse.notification.entity.Notification;
import ru.warehouse.notification.repository.NotificationRepository;

/**
 * Приём событий из Kafka. Обработка идемпотентна: повторная доставка того же eventId
 * не создаёт дубль (проверка + уникальный индекс event_id + recipient_id в БД).
 * Нечитаемое сообщение пропускается (повтор не поможет), ошибки БД пробрасываются:
 * потребитель повторит попытку и затем отправит сообщение в DLT.
 */
@Service
public class NotificationIngestService {

    private static final Logger log = LoggerFactory.getLogger(NotificationIngestService.class);
    private static final String TRACE_KEY = "traceId"; // привести к ключу MDC из common-lib

    private final JsonMapper jsonMapper;
    private final RequestNotificationMapper mapper;
    private final NotificationRepository repository;

    public NotificationIngestService(JsonMapper jsonMapper,
                                     RequestNotificationMapper mapper,
                                     NotificationRepository repository) {
        this.jsonMapper = jsonMapper;
        this.mapper = mapper;
        this.repository = repository;
    }

    @Transactional
    public void handle(String raw) {
        EventEnvelope event;
        try {
            event = jsonMapper.readValue(raw, EventEnvelope.class);
        } catch (JacksonException e) {
            log.warn("Skipping unreadable event: {}", e.getOriginalMessage());
            return;
        }
        if (event.eventId() == null) {
            log.warn("Skipping event without eventId, action={}", event.action());
            return;
        }

        if (event.traceId() != null) {
            MDC.put(TRACE_KEY, event.traceId());
        }
        try {
            mapper.map(event).ifPresentOrElse(
                    draft -> save(event, draft),
                    () -> log.debug("Event {} ({}) produces no notification", event.eventId(), event.action()));
        } finally {
            MDC.remove(TRACE_KEY);
        }
    }

    private void save(EventEnvelope event, NotificationDraft draft) {
        if (repository.existsByEventIdAndRecipientId(event.eventId(), draft.recipientId())) {
            log.debug("Duplicate event {} for recipient {}, skipped", event.eventId(), draft.recipientId());
            return;
        }
        repository.save(Notification.create(
                draft.recipientId(), draft.type(), draft.title(), draft.message(),
                draft.requestId(), event.eventId(), event.traceId()));
        log.info("Notification {} created for request {}", draft.type(), draft.requestId());
    }
}
