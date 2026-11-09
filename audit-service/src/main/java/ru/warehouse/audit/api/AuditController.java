package ru.warehouse.audit.api;

import ru.warehouse.audit.model.AuditPage;
import ru.warehouse.audit.service.AuditLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit/events")
public class AuditController {

    private final AuditLogService service;

    public AuditController(AuditLogService service) {
        this.service = service;
    }

    /**
     * Filters: entityType, entityId, actorId, action (exact, or prefix with a trailing *, e.g. request.*),
     * from (inclusive) and to (exclusive) as ISO-8601 instants. Newest first.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AuditPage list(@RequestParam(required = false) String entityType,
                          @RequestParam(required = false) String entityId,
                          @RequestParam(required = false) UUID actorId,
                          @RequestParam(required = false) String action,
                          @RequestParam(required = false) Instant from,
                          @RequestParam(required = false) Instant to,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "50") int size) {
        return service.search(entityType, entityId, actorId, action, from, to, page, size);
    }
}
