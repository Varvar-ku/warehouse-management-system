package ru.warehouse.inventory.service;

import org.springframework.stereotype.Component;
import ru.warehouse.common.event.OutboxPublisher;
import ru.warehouse.common.event.StockMovementEvent;
import ru.warehouse.inventory.entity.Batch;
import ru.warehouse.inventory.entity.StockMovement;

import java.util.Map;

/**
 * Публикует снимок движения остатка для report-service. Вызывать сразу после сохранения движения
 * (в той же транзакции): событие попадёт в outbox вместе с бизнес-изменением.
 */
@Component
public class MovementEvents {

    private final OutboxPublisher outbox;

    public MovementEvents(OutboxPublisher outbox) {
        this.outbox = outbox;
    }

    public void publish(StockMovement m, Batch b, String documentNumber, Map<String, Object> context) {
        outbox.publishStockMovement(new StockMovementEvent(
                m.getId(),                                   // eventId = id движения: повтор безвреден
                StockMovementEvent.CURRENT_SCHEMA,
                m.getId(),
                m.getMovementType().name(),
                m.getWarehouseId(),
                m.getProductId(),
                b.getProductSku(),
                b.getProductName(),
                m.getUnitCode(),
                b.getId(),
                b.getBatchNumber(),
                b.getExpiryAt() == null ? null : b.getExpiryAt().toInstant(),
                m.getFromSectionId(),
                m.getFromSectionCode(),
                m.getToSectionId(),
                m.getToSectionCode(),
                m.getQuantity(),
                m.getDocumentType(),
                m.getDocumentId(),
                documentNumber,
                m.getPerformedBy(),
                m.getPerformedAt().toInstant(),
                m.getComment(),
                null,                                        // traceId подставит OutboxPublisher из MDC
                context));
    }
}
