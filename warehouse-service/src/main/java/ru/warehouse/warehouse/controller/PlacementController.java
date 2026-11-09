// PlacementController.java
package ru.warehouse.warehouse.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.warehouse.placement.PlacementModels.PlacementRequest;
import ru.warehouse.warehouse.placement.PlacementModels.PlacementResult;
import ru.warehouse.warehouse.placement.PlacementModels.SectionFit;
import ru.warehouse.warehouse.placement.PlacementModels.ValidateRequest;
import ru.warehouse.warehouse.service.PlacementService;
import java.util.List;

/**
 * Расчёт рекомендуемых секций. Складская операция кладовщика: ADMIN её автоматически не получает (разд. 4 ТЗ).
 * Эндпоинт повторяемый (ничего не сохраняет), поэтому Idempotency-Key не нужен.
 * На этапе 4 его вызовет inventory-service из POST /receipts/{id}/recommend-placement, передавая JWT пользователя.
 */
@RestController
@RequestMapping("/api/v1/placement")
@RequiredArgsConstructor
public class PlacementController {
    private final PlacementService placementService;

    @PostMapping("/recommend")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public List<PlacementResult> recommend(@Valid @RequestBody PlacementRequest request) {
        return placementService.recommend(request);
    }

    /**
     * Подходит ли секция позиции. Нужен работнику склада при подтверждении размещения
     * (inventory-service пересылает его JWT), поэтому роль шире, чем у recommend. Ничего не сохраняет.
     */
    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('STOREKEEPER', 'WAREHOUSE_WORKER')")
    public SectionFit validate(@Valid @RequestBody ValidateRequest request) {
        return placementService.validate(request);
    }
}