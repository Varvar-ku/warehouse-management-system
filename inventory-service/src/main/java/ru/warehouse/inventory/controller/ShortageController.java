// ShortageController.java
package ru.warehouse.inventory.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.inventory.dto.AdjustmentDtos.FoundRequest;
import ru.warehouse.inventory.dto.AdjustmentDtos.ShortageResponse;
import ru.warehouse.inventory.entity.StockShortage;
import ru.warehouse.inventory.service.ShortageService;
import java.util.UUID;

/**
 * Недостачи при сборе. Просмотр: кладовщик и администратор. Решение «найдено»: кладовщик.
 * Реальная недостача закрывается документом корректировки (AdjustmentController).
 */
@RestController
@RequestMapping("/api/v1/shortages")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STOREKEEPER', 'ADMIN')")
public class ShortageController {
    private final ShortageService shortageService;

    @GetMapping
    public PageResponse<ShortageResponse> search(@RequestParam(required = false) StockShortage.Status status,
                                                 @RequestParam(required = false) UUID warehouseId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return shortageService.search(status, warehouseId, page, size);
    }

    @GetMapping("/{id}")
    public ShortageResponse get(@PathVariable UUID id) {
        return shortageService.get(id);
    }

    @PostMapping("/{id}/found")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public ShortageResponse found(@PathVariable UUID id, @Valid @RequestBody FoundRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return shortageService.found(id, request.comment(), UUID.fromString(jwt.getSubject()));
    }
}
