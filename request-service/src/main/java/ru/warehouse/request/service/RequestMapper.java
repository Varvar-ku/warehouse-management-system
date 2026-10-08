// RequestMapper.java
package ru.warehouse.request.service;

import ru.warehouse.request.dto.RequestDtos.*;
import ru.warehouse.request.entity.Request;
import ru.warehouse.request.entity.RequestItem;
import ru.warehouse.request.entity.StatusHistory;

/** Преобразование в ответы. Вызывать внутри транзакции: позиции загружаются лениво. */
final class RequestMapper {
    private RequestMapper() {}

    static RequestResponse toResponse(Request r) {
        return new RequestResponse(r.getId(), r.getNumber(), r.getRequesterId(), r.getWarehouseId(), r.getStatus(),
                r.getPriority(), r.getComment(), r.getStatusReason(), r.getCreatedAt(), r.getSubmittedAt(),
                r.getReviewedBy(), r.getReviewedAt(), r.getClosedAt(),
                r.getItems().stream().map(RequestMapper::toItem).toList());
    }

    static RequestSummary toSummary(Request r) {
        return new RequestSummary(r.getId(), r.getNumber(), r.getRequesterId(), r.getWarehouseId(), r.getStatus(),
                r.getPriority(), r.getCreatedAt(), r.getSubmittedAt(), r.getItems().size());
    }

    static RequestItemResponse toItem(RequestItem i) {
        // «Не обеспечено» известно только после проверки наличия
        var unavailable = i.getApprovedQuantity() == null ? null
                : i.getRequestedQuantity().subtract(i.getApprovedQuantity());
        return new RequestItemResponse(i.getId(), i.getLineNo(), i.getProductId(), i.getProductSku(),
                i.getProductName(), i.getUnitCode(), i.getRequestedQuantity(), i.getApprovedQuantity(),
                i.getReservedQuantity(), i.getIssuedQuantity(), unavailable, i.getComment());
    }

    static HistoryResponse toHistory(StatusHistory h) {
        return new HistoryResponse(h.getFromStatus(), h.getToStatus(), h.getActorId(), h.getComment(), h.getCreatedAt());
    }
}