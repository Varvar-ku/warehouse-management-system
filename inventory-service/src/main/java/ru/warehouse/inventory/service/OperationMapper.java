// OperationMapper.java
package ru.warehouse.inventory.service;

import ru.warehouse.inventory.dto.OperationDtos.*;
import ru.warehouse.inventory.entity.*;
import java.util.List;

/** Преобразование в ответы. Вызывать внутри транзакции: связи загружаются лениво. */
final class OperationMapper {
    private OperationMapper() {}

    static IssueResponse toResponse(Issue i) {
        List<IssueItemResponse> items = i.getItems().stream().map(it -> {
            Stock s = it.getStock();
            Batch b = s.getBatch();
            return new IssueItemResponse(it.getId(), it.getRequestItemId(), it.getProductId(), b.getProductSku(),
                    b.getProductName(), s.getId(), s.getSectionCode(), b.getBatchNumber(), s.getUnitCode(),
                    it.getQuantity());
        }).toList();
        return new IssueResponse(i.getId(), i.getNumber(), i.getWarehouseId(), i.getRequestId(), i.getIssuedTo(),
                i.getIssuedBy(), i.getStatus(), i.getCreatedAt(), i.getIssuedAt(), i.getComment(), items);
    }

    static WriteOffResponse toResponse(WriteOff w) {
        List<WriteOffItemResponse> items = w.getItems().stream().map(it -> {
            Stock s = it.getStock();
            Batch b = s.getBatch();
            return new WriteOffItemResponse(it.getId(), s.getId(), it.getProductId(), b.getProductSku(),
                    b.getProductName(), s.getSectionCode(), b.getBatchNumber(), s.getUnitCode(), it.getQuantity());
        }).toList();
        return new WriteOffResponse(w.getId(), w.getNumber(), w.getWarehouseId(), w.getReason(), w.getComment(),
                w.getStatus(), w.isApprovalRequired(), w.getApprovedBy(), w.getApprovedAt(), w.getCreatedBy(),
                w.getCreatedAt(), w.getConfirmedBy(), w.getConfirmedAt(), items);
    }

    static ReservationResponse toResponse(Reservation r) {
        Stock s = r.getStock();
        Batch b = s.getBatch();
        return new ReservationResponse(r.getId(), r.getRequestId(), r.getRequestItemId(), s.getId(),
                s.getSectionId(), s.getSectionCode(), b.getId(), b.getBatchNumber(),
                b.getProductSku(), r.getQuantity(), r.getConsumedQuantity(),
                r.getReleasedQuantity(), r.getStatus(), r.getCreatedAt(), r.getClosedAt());
    }
}
