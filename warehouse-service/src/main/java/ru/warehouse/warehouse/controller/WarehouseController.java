// WarehouseController.java
package ru.warehouse.warehouse.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.warehouse.dto.WarehouseDtos.*;
import ru.warehouse.warehouse.service.WarehouseService;
import ru.warehouse.warehouse.service.WarehouseUsageGuard;
import java.util.List;
import java.util.UUID;

/** Чтение: любая роль. Изменение: ADMIN (разд. 4 ТЗ). */
@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {
    private final WarehouseService warehouseService;
    private final WarehouseUsageGuard usageGuard;

    @GetMapping
    public List<WarehouseResponse> list() {
        return warehouseService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public WarehouseResponse create(@Valid @RequestBody WarehouseRequest request) {
        return warehouseService.create(request);
    }

    @GetMapping("/{id}")
    public WarehouseResponse get(@PathVariable UUID id) {
        return warehouseService.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public WarehouseResponse update(@PathVariable UUID id, @Valid @RequestBody WarehouseUpdateRequest request) {
        if (Boolean.FALSE.equals(request.active()) && warehouseService.get(id).active()) {
            usageGuard.assertCanDeactivate(id); // M4: остатки и открытые документы, до транзакции изменения
        }
        return warehouseService.update(id, request);
    }
}