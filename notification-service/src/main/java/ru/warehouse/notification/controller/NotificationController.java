package ru.warehouse.notification.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.warehouse.notification.dto.NotificationPageResponse;
import ru.warehouse.notification.dto.NotificationResponse;
import ru.warehouse.notification.dto.UnreadCountResponse;
import ru.warehouse.notification.service.NotificationService;

import java.util.Map;
import java.util.UUID;

/** Каждый пользователь видит и меняет только свои уведомления (владелец берётся из JWT, не из запроса). */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final String ANY_ROLE = "hasAnyRole('USER','STOREKEEPER','WAREHOUSE_WORKER','ADMIN')";

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ANY_ROLE)
    public NotificationPageResponse list(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(defaultValue = "false") boolean unreadOnly,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(actor(jwt), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    @PreAuthorize(ANY_ROLE)
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return service.unreadCount(actor(jwt));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize(ANY_ROLE)
    public NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.markRead(actor(jwt), id);
    }

    @PostMapping("/read-all")
    @PreAuthorize(ANY_ROLE)
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("updated", service.markAllRead(actor(jwt)));
    }

    // Предполагается, что sub в JWT = UUID пользователя. Если в common-lib уже есть actor(Jwt), используйте его.
    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
