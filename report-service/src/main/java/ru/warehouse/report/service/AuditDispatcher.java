package ru.warehouse.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import ru.warehouse.report.dto.AuditEnvelope;

/** Разбирает событие аудита один раз и передаёт нужному обработчику. Чужие события отсеиваются без разбора JSON. */
@Component
public class AuditDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AuditDispatcher.class);

    private final JsonMapper jsonMapper;
    private final PickingEventIngestService picking;
    private final RequestFactIngestService requestFacts;
    private final DiscrepancyIngestService discrepancies;

    public AuditDispatcher(JsonMapper jsonMapper, PickingEventIngestService picking,
                           RequestFactIngestService requestFacts, DiscrepancyIngestService discrepancies) {
        this.jsonMapper = jsonMapper;
        this.picking = picking;
        this.requestFacts = requestFacts;
        this.discrepancies = discrepancies;
    }

    public void handle(String raw) {
        if (!relevant(raw)) {
            return;
        }
        AuditEnvelope e;
        try {
            e = jsonMapper.readValue(raw, AuditEnvelope.class);
        } catch (JacksonException ex) {
            log.warn("Skipping unreadable audit event: {}", ex.getOriginalMessage());
            return;
        }
        if (e.eventId() == null || e.occurredAt() == null || e.action() == null) {
            return;
        }
        String action = e.action();
        if (action.startsWith("picking.")) {
            picking.handle(e);
        } else if (action.equals("request.status_changed")) {
            requestFacts.handle(e);
        } else if (action.equals("request.discrepancy")
                || action.equals("receipt.placement_discrepancy")
                || action.equals("receipt.discrepancy_resolved")) {
            discrepancies.handle(e);
        }
    }

    private static boolean relevant(String raw) {
        return raw.contains("\"picking.") || raw.contains("request.status_changed")
                || raw.contains("request.discrepancy") || raw.contains("receipt.placement_discrepancy")
                || raw.contains("receipt.discrepancy_resolved");
    }
}
