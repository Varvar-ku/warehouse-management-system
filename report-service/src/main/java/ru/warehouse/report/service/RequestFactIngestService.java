package ru.warehouse.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.report.dto.AuditEnvelope;
import ru.warehouse.report.repository.RequestFactRepository;

import java.util.Map;
import java.util.UUID;

import static ru.warehouse.report.service.AuditValues.dec;
import static ru.warehouse.report.service.AuditValues.instant;
import static ru.warehouse.report.service.AuditValues.integer;
import static ru.warehouse.report.service.AuditValues.str;
import static ru.warehouse.report.service.AuditValues.uuid;

/** Снимок заявки по событию request.status_changed (request-service). Количества и дата создания идут в details. */
@Service
public class RequestFactIngestService {

    private final RequestFactRepository repository;

    public RequestFactIngestService(RequestFactRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void handle(AuditEnvelope e) {
        if (!"request-service".equals(e.service())) {
            return;
        }
        UUID requestId = uuid(e.entityId());
        Map<String, Object> d = e.details();
        if (requestId == null || d == null || str(d.get("requestNumber")) == null || str(d.get("newStatus")) == null) {
            return;
        }
        repository.upsert(requestId, str(d.get("requestNumber")), uuid(d.get("requesterId")),
                uuid(d.get("warehouseId")), str(d.get("newStatus")), integer(d.get("itemsCount")),
                dec(d.get("requestedQuantity")), dec(d.get("approvedQuantity")), dec(d.get("reservedQuantity")),
                dec(d.get("issuedQuantity")), instant(d.get("createdAt")), str(d.get("issueNumber")),
                e.occurredAt(), e.eventId());
    }
}
