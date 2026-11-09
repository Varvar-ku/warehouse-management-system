// ShortageMapper.java
package ru.warehouse.inventory.service;

import ru.warehouse.inventory.dto.AdjustmentDtos.*;
import ru.warehouse.inventory.entity.*;
import java.util.List;

/** Преобразование в ответы. Вызывать внутри транзакции: связи загружаются лениво. */
final class ShortageMapper {
    private ShortageMapper() {}

    static ShortageResponse toResponse(StockShortage s) {
        Stock st = s.getStock();
        Batch b = st.getBatch();
        return new ShortageResponse(s.getId(), s.getNumber(), s.getWarehouseId(), s.getProductId(),
                b.getProductSku(), b.getProductName(), st.getId(), st.getSectionCode(), b.getBatchNumber(),
                s.getUnitCode(), s.getQuantity(), s.getRequestId(), s.getRequestItemId(), s.getIssueId(),
                s.getSource(), s.getStatus(), s.getResolution(), s.getResolutionComment(), s.getAdjustmentId(),
                s.getResolvedBy(), s.getResolvedAt(), s.getCreatedAt());
    }

    static AdjustmentResponse toResponse(StockAdjustment a) {
        List<AdjustmentItemResponse> items = a.getItems().stream().map(it -> {
            Stock st = it.getStock();
            Batch b = st.getBatch();
            return new AdjustmentItemResponse(it.getId(), it.getShortage().getId(), it.getShortage().getNumber(),
                    st.getId(), it.getProductId(), b.getProductSku(), b.getProductName(), st.getSectionCode(),
                    b.getBatchNumber(), st.getUnitCode(), it.getQuantity());
        }).toList();
        return new AdjustmentResponse(a.getId(), a.getNumber(), a.getWarehouseId(), a.getComment(), a.getStatus(),
                a.getCreatedBy(), a.getCreatedAt(), a.getApprovedBy(), a.getApprovedAt(), a.getConfirmedBy(),
                a.getConfirmedAt(), a.getCancelReason(), items);
    }
}
