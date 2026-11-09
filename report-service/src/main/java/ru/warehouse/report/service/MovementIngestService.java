package ru.warehouse.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import ru.warehouse.common.event.StockMovementEvent;
import ru.warehouse.report.repository.MovementFactRepository;

/**
 * Приём событий движения остатка. Идемпотентно: повторная доставка (id движения) вставку не дублирует.
 * Нечитаемое сообщение пропускается, ошибки БД пробрасываются (повторы, затем DLT).
 */
@Service
public class MovementIngestService {

    private static final Logger log = LoggerFactory.getLogger(MovementIngestService.class);

    private final JsonMapper jsonMapper;
    private final MovementFactRepository repository;

    public MovementIngestService(JsonMapper jsonMapper, MovementFactRepository repository) {
        this.jsonMapper = jsonMapper;
        this.repository = repository;
    }

    @Transactional
    public void handle(String raw) {
        StockMovementEvent event;
        try {
            event = jsonMapper.readValue(raw, StockMovementEvent.class);
        } catch (JacksonException e) {
            log.warn("Skipping unreadable movement event: {}", e.getOriginalMessage());
            return;
        }
        if (event.movementId() == null || event.movementType() == null || event.quantity() == null
                || event.warehouseId() == null || event.productId() == null || event.documentId() == null
                || event.performedBy() == null || event.performedAt() == null) {
            log.warn("Skipping incomplete movement event {}", event.eventId());
            return;
        }
        String context = event.context() == null ? "{}" : jsonMapper.writeValueAsString(event.context());
        int inserted = repository.insert(event, context);
        if (inserted == 0) {
            log.debug("Duplicate movement event {}, skipped", event.movementId());
        }
    }
}
