// SectionController.java
package ru.warehouse.warehouse.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.warehouse.dto.WarehouseDtos.*;
import ru.warehouse.warehouse.entity.SectionStatus;
import ru.warehouse.warehouse.service.SectionConstraintsValidator;
import ru.warehouse.warehouse.service.SectionService;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sections")
@RequiredArgsConstructor
public class SectionController {
    private final SectionService sectionService;
    private final SectionConstraintsValidator constraintsValidator;

    @GetMapping
    @PreAuthorize("hasAnyRole('STOREKEEPER', 'ADMIN', 'WAREHOUSE_WORKER')")
    public PageResponse<SectionResponse> search(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) SectionStatus status,
            @RequestParam(required = false) String q,                       // поиск по коду и названию
            @RequestParam(required = false) String zone,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {
        return sectionService.search(warehouseId, status, q, zone, includeArchived, page, size, sortBy, direction);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SectionResponse create(@Valid @RequestBody CreateSectionRequest request) {
        constraintsValidator.validate(request.constraints()); // M5: до транзакции
        return sectionService.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STOREKEEPER', 'ADMIN', 'WAREHOUSE_WORKER')")
    public SectionResponse get(@PathVariable UUID id) {
        return sectionService.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SectionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSectionRequest request) {
        constraintsValidator.validate(request.constraints()); // M5: до транзакции
        return sectionService.update(id, request);
    }
}