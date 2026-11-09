package ru.warehouse.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import ru.warehouse.report.dto.AuditEnvelope;
import ru.warehouse.report.repository.PickingEventRepository;

import java.util.Map;
import java.util.UUID;

import static ru.warehouse.report.service.AuditValues.str;
import static ru.warehouse.report.service.AuditValues.uuid;

/** События комплектации (service=request-service, action=picking.*). Повторная доставка eventId не дублирует. */
@Service
public class PickingEventIngestService {

    private static final Logger log = LoggerFactory.getLogger(PickingEventIngestService.class);
    private static final String SOURCE = "request-service";
    private static final String ITEM_PICKED = "picking.item_picked";

    private final JsonMapper jsonMapper;
    private final PickingEventRepository repository;

    public PickingEventIngestService(JsonMapper jsonMapper, PickingEventRepository repository) {
        this.jsonMapper = jsonMapper;
        this.repository = repository;
    }

    @Transactional
    public void handle(AuditEnvelope e) {
        if (!SOURCE.equals(e.service())) {
            return;
        }
        Map<String, Object> details = e.details() == null ? Map.of() : e.details();
        UUID actor = uuid(e.actorId());
        UUID worker = uuid(details.get("workerId"));
        if (worker == null && ITEM_PICKED.equals(e.action())) {
            worker = actor; // собирает строку сам работник
        }
        int inserted = repository.insert(e.eventId(), e.occurredAt(), e.action(), uuid(e.entityId()),
                str(details.get("taskNumber")), actor, worker, e.traceId(),
                jsonMapper.writeValueAsString(details));
        if (inserted == 0) {
            log.debug("Duplicate picking event {}, skipped", e.eventId());
        }
    }
}
