// StockController.java
package ru.warehouse.inventory.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.inventory.dto.InventoryDtos.MovementResponse;
import ru.warehouse.inventory.dto.InventoryDtos.StockResponse;
import ru.warehouse.inventory.entity.StockMovement;
import ru.warehouse.inventory.service.StockQueryService;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Остатки и журнал движений: просмотр для кладовщика, администратора, работника склада */
@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STOREKEEPER', 'ADMIN', 'WAREHOUSE_WORKER')")
public class StockController {
    private final StockQueryService queryService;

    @GetMapping
    public PageResponse<StockResponse> stock(@RequestParam(required = false) UUID warehouseId,
                                             @RequestParam(required = false) UUID sectionId,
                                             @RequestParam(required = false) UUID productId,
                                             @RequestParam(defaultValue = "false") boolean onlyAvailable,
                                             @RequestParam(defaultValue = "false") boolean includeZero,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return queryService.stock(warehouseId, sectionId, productId, onlyAvailable, includeZero, page, size);
    }

    @GetMapping("/movements")
    public PageResponse<MovementResponse> movements(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) StockMovement.Type type,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) UUID documentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.movements(warehouseId, productId, sectionId, type, documentType, documentId,
                from, to, page, size);
    }
}