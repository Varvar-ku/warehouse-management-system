// PlacementTaskController.java
package ru.warehouse.inventory.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.inventory.dto.InventoryDtos.*;
import ru.warehouse.inventory.entity.PlacementTask;
import ru.warehouse.inventory.service.PlacementTaskService;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/placement-tasks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STOREKEEPER', 'WAREHOUSE_WORKER')")
public class PlacementTaskController {
    private final PlacementTaskService taskService;

    @GetMapping
    public List<PlacementTaskResponse> list(@RequestParam(required = false) PlacementTask.Status status,
                                            @AuthenticationPrincipal Jwt jwt) {
        return taskService.list(status, isStorekeeper(jwt), user(jwt));
    }

    @GetMapping("/{id}")
    public PlacementTaskResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return taskService.get(id, isStorekeeper(jwt), user(jwt));
    }

    /** Работник склада подтверждает фактическую секцию и количество. */
    @PostMapping("/{id}/items/{itemId}/confirm")
    @PreAuthorize("hasRole('WAREHOUSE_WORKER')")
    public PlacementTaskResponse confirmItem(@PathVariable UUID id, @PathVariable UUID itemId,
                                             @Valid @RequestBody ConfirmPlacementRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        return taskService.confirmItem(id, itemId, request, user(jwt));
    }

    /** Кладовщик решает вопрос по расхождению. */
    @PostMapping("/{id}/items/{itemId}/resolve")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public PlacementTaskResponse resolve(@PathVariable UUID id, @PathVariable UUID itemId,
                                         @Valid @RequestBody ResolveRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return taskService.resolve(id, itemId, request, user(jwt));
    }

    private static UUID user(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static boolean isStorekeeper(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("STOREKEEPER");
    }
}