// ReceiptController.java
package ru.warehouse.inventory.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.inventory.dto.InventoryDtos.*;
import ru.warehouse.inventory.entity.Receipt;
import ru.warehouse.inventory.service.ReceiptService;
import java.util.List;
import java.util.UUID;

/** Поступления: складская операция кладовщика (разд. 4 ТЗ). ADMIN доступа не получает. */
@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STOREKEEPER')")
public class ReceiptController {
    private final ReceiptService receiptService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptResponse create(@Valid @RequestBody CreateReceiptRequest request, @AuthenticationPrincipal Jwt jwt) {
        return receiptService.create(request, user(jwt));
    }

    @GetMapping
    public PageResponse<ReceiptSummary> search(@RequestParam(required = false) Receipt.Status status,
                                               @RequestParam(required = false) UUID warehouseId,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return receiptService.search(status, warehouseId, q, page, size);
    }

    @GetMapping("/{id}")
    public ReceiptResponse get(@PathVariable UUID id) {
        return receiptService.get(id);
    }

    @PatchMapping("/{id}")
    public ReceiptResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateReceiptRequest request) {
        return receiptService.update(id, request);
    }

    @PostMapping("/{id}/recommend-placement")
    public List<ItemRecommendationResponse> recommend(@PathVariable UUID id) {
        return receiptService.recommend(id);
    }

    @PostMapping("/{id}/confirm")
    public ReceiptResponse confirm(@PathVariable UUID id,
                                   @Valid @RequestBody(required = false) ConfirmReceiptRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return receiptService.confirm(id, request, user(jwt));
    }

    @PostMapping("/{id}/placement-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public PlacementTaskResponse createPlacementTask(@PathVariable UUID id,
                                                     @Valid @RequestBody(required = false) CreatePlacementTaskRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return receiptService.createPlacementTask(id, request == null ? null : request.assigneeId(), user(jwt));
    }

    @PostMapping("/{id}/cancel")
    public ReceiptResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return receiptService.cancel(id, request.reason(), user(jwt));
    }

    private static UUID user(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}