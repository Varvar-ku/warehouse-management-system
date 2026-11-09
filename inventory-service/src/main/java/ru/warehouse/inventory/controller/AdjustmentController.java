// AdjustmentController.java
package ru.warehouse.inventory.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.inventory.dto.AdjustmentDtos.AdjustmentResponse;
import ru.warehouse.inventory.dto.AdjustmentDtos.CreateAdjustmentRequest;
import ru.warehouse.inventory.dto.InventoryDtos.CancelRequest;
import ru.warehouse.inventory.entity.StockAdjustment;
import ru.warehouse.inventory.service.AdjustmentService;
import java.util.UUID;

/**
 * Корректировки остатка по недостачам. Просмотр: кладовщик и администратор. Оформление и проведение: кладовщик.
 * Одобрение (обязательно): администратор, не автор документа. POST .../confirm требует Idempotency-Key.
 */
@RestController
@RequestMapping("/api/v1/adjustments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STOREKEEPER', 'ADMIN')")
public class AdjustmentController {
    private final AdjustmentService adjustmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STOREKEEPER')")
    public AdjustmentResponse create(@Valid @RequestBody CreateAdjustmentRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return adjustmentService.create(request, user(jwt));
    }

    @GetMapping
    public PageResponse<AdjustmentResponse> search(@RequestParam(required = false) StockAdjustment.Status status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return adjustmentService.search(status, page, size);
    }

    @GetMapping("/{id}")
    public AdjustmentResponse get(@PathVariable UUID id) {
        return adjustmentService.get(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public AdjustmentResponse approve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return adjustmentService.approve(id, user(jwt));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public AdjustmentResponse confirm(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return adjustmentService.confirm(id, user(jwt));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public AdjustmentResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return adjustmentService.cancel(id, request.reason(), user(jwt));
    }

    private static UUID user(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
