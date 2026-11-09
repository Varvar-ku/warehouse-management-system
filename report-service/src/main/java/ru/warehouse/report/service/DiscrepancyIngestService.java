package ru.warehouse.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.report.dto.AuditEnvelope;
import ru.warehouse.report.repository.DiscrepancyRepository;

import java.util.Map;
import java.util.UUID;

import static ru.warehouse.report.service.AuditValues.dec;
import static ru.warehouse.report.service.AuditValues.integer;
import static ru.warehouse.report.service.AuditValues.str;
import static ru.warehouse.report.service.AuditValues.uuid;

/**
 * Расхождения из аудит-событий:
 * request.discrepancy (request-service): запрошено больше, чем выдано;
 * receipt.placement_discrepancy и receipt.discrepancy_resolved (inventory-service): приёмка.
 */
@Service
public class DiscrepancyIngestService {

    private final DiscrepancyRepository repository;

    public DiscrepancyIngestService(DiscrepancyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void handle(AuditEnvelope e) {
        Map<String, Object> d = e.details() == null ? Map.of() : e.details();
        UUID documentId = uuid(e.entityId());
        UUID actor = uuid(e.actorId());
        switch (e.action()) {
            case "request.discrepancy" -> {
                if ("request-service".equals(e.service())) {
                    repository.insert(e.eventId(), e.occurredAt(), "REQUEST", documentId,
                            str(d.get("requestNumber")), integer(d.get("lineNo")),
                            str(d.get("productSku")), str(d.get("productName")), str(d.get("unitCode")),
                            dec(d.get("requestedQuantity")), dec(d.get("issuedQuantity")), str(d.get("reason")),
                            actor, null, null, str(d.get("issueNumber")));
                }
            }
            case "receipt.placement_discrepancy" -> {
                if ("inventory-service".equals(e.service())) {
                    repository.insert(e.eventId(), e.occurredAt(), "RECEIPT", documentId,
                            str(d.get("number")), integer(d.get("line")),
                            str(d.get("productSku")), str(d.get("productName")), str(d.get("unitCode")),
                            dec(d.get("plannedQuantity")), dec(d.get("actualQuantity")), str(d.get("comment")),
                            actor, str(d.get("plannedSection")), str(d.get("actualSection")), null);
                }
            }
            case "receipt.discrepancy_resolved" -> {
                if ("inventory-service".equals(e.service()) && documentId != null) {
                    repository.resolveReceipt(documentId, integer(d.get("line")), str(d.get("decision")),
                            str(d.get("comment")), e.occurredAt(), actor);
                }
            }
            default -> {
            }
        }
    }
}
