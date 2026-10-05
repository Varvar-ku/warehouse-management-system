// InventoryMapper.java
package ru.warehouse.inventory.service;

import ru.warehouse.inventory.client.WarehouseContract.PlacementResult;
import ru.warehouse.inventory.dto.InventoryDtos.*;
import ru.warehouse.inventory.entity.*;
import java.util.Arrays;
import java.util.List;

/** Преобразование сущностей в ответы. Вызывать внутри транзакции: связи загружаются лениво. */
public final class InventoryMapper {
    private InventoryMapper() {}

    public static ReceiptResponse toResponse(Receipt r) {
        return new ReceiptResponse(r.getId(), r.getNumber(), r.getWarehouseId(), r.getSupplierName(),
                r.getExternalDocumentNumber(), r.getStatus(), r.getReceivedAt(), r.getCreatedBy(), r.getCreatedAt(),
                r.getConfirmedAt(), r.getCompletedAt(), r.getCancelReason(),
                r.getItems().stream().map(InventoryMapper::toItem).toList());
    }

    public static ReceiptSummary toSummary(Receipt r) {
        return new ReceiptSummary(r.getId(), r.getNumber(), r.getWarehouseId(), r.getSupplierName(),
                r.getExternalDocumentNumber(), r.getStatus(), r.getReceivedAt(), r.getItems().size());
    }

    static ReceiptItemResponse toItem(ReceiptItem i) {
        RecommendationInfo rec = i.getRecommendationCalculatedAt() == null ? null
                : new RecommendationInfo(i.getRecommendedSectionId(), i.getRecommendedSectionCode(),
                i.getRecommendationStrategy(), i.getRuleId(), i.getRuleVersion(), i.getRuleName(),
                i.getRecommendationCalculatedAt(), splitNotes(i.getRecommendationNotes()));
        return new ReceiptItemResponse(i.getId(), i.getLineNo(), i.getProductId(), i.getProductSku(),
                i.getProductName(), i.getUnitCode(), i.getQuantity(), i.effectiveBatchNumber(), i.getExpiryAt(),
                i.getComment(), rec, i.getTargetSectionId(), i.getTargetSectionCode(), i.getTargetDecision(),
                i.getPlacementStatus(), i.getPlacedSectionId(), i.getPlacedSectionCode(), i.getPlacedQuantity(),
                i.getDiscrepancyComment(), i.getBatchId());
    }

    public static PlacementTaskResponse toTaskResponse(PlacementTask t) {
        Receipt r = t.getReceipt();
        List<TaskItemResponse> items = r.getItems().stream()
                .map(i -> new TaskItemResponse(i.getId(), i.getLineNo(), i.getProductId(), i.getProductSku(),
                        i.getProductName(), i.getUnitCode(), i.getQuantity(), i.getTargetSectionId(),
                        i.getTargetSectionCode(), i.getPlacementStatus(), i.getPlacedSectionCode(),
                        i.getPlacedQuantity(), i.getDiscrepancyComment()))
                .toList();
        return new PlacementTaskResponse(t.getId(), r.getId(), r.getNumber(), r.getWarehouseId(), t.getStatus(),
                t.getAssigneeId(), t.getCreatedAt(), t.getCompletedAt(), items);
    }

    public static ItemRecommendationResponse toRecommendation(ReceiptItem i, PlacementResult pr) {
        List<CandidateDto> candidates = pr.candidates() == null ? List.of()
                : pr.candidates().stream()
                .map(c -> new CandidateDto(c.sectionId(), c.sectionCode(), c.freeCapacity())).toList();
        return new ItemRecommendationResponse(i.getId(), i.getLineNo(), pr.status(), pr.strategy(),
                pr.sectionId(), pr.sectionCode(), pr.ruleId(), pr.ruleVersion(), pr.ruleName(), pr.calculatedAt(),
                pr.requiresStorekeeperAction(), pr.notes() == null ? List.of() : pr.notes(), candidates);
    }

    public static StockResponse toResponse(Stock s) {
        Batch b = s.getBatch();
        return new StockResponse(s.getId(), s.getWarehouseId(), s.getProductId(), b.getProductSku(),
                b.getProductName(), s.getSectionId(), s.getSectionCode(), b.getId(), b.getBatchNumber(),
                s.getUnitCode(), s.getQuantity(), s.getReservedQuantity(), s.available());
    }

    public static MovementResponse toResponse(StockMovement m) {
        return new MovementResponse(m.getId(), m.getMovementType(), m.getWarehouseId(), m.getProductId(),
                m.getBatchId(), m.getFromSectionId(), m.getFromSectionCode(), m.getToSectionId(),
                m.getToSectionCode(), m.getQuantity(), m.getUnitCode(), m.getDocumentType(), m.getDocumentId(),
                m.getPerformedBy(), m.getPerformedAt(), m.getComment());
    }

    private static List<String> splitNotes(String notes) {
        return notes == null || notes.isBlank() ? List.of() : Arrays.asList(notes.split("\n"));
    }
}