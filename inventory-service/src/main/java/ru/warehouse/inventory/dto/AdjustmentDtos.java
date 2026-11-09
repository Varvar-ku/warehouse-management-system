// AdjustmentDtos.java
package ru.warehouse.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.warehouse.inventory.entity.StockAdjustment;
import ru.warehouse.inventory.entity.StockShortage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** B1: недостачи и документы корректировки. */
public final class AdjustmentDtos {
    private AdjustmentDtos() {}

    /** Решение «товар найден, недостачи нет»: остаток возвращается в оборот. Комментарий обязателен. */
    public record FoundRequest(@NotBlank @Size(max = 1000) String comment) {}

    public record AdjustmentItemRequest(@NotNull UUID shortageId) {}

    /** Корректировка уменьшает остаток на количество недостачи по каждой включённой недостаче. */
    public record CreateAdjustmentRequest(
            @NotNull UUID warehouseId,
            @NotBlank @Size(max = 1000) String comment,
            @NotEmpty @Size(max = 200) List<@Valid AdjustmentItemRequest> items) {}

    public record ShortageResponse(
            UUID id, String number, UUID warehouseId, UUID productId, String productSku, String productName,
            UUID stockId, String sectionCode, String batchNumber, String unitCode, BigDecimal quantity,
            UUID requestId, UUID requestItemId, UUID issueId, StockShortage.Source source,
            StockShortage.Status status, StockShortage.Resolution resolution, String resolutionComment,
            UUID adjustmentId, UUID resolvedBy, OffsetDateTime resolvedAt, OffsetDateTime createdAt) {}

    public record AdjustmentItemResponse(
            UUID id, UUID shortageId, String shortageNumber, UUID stockId, UUID productId, String productSku,
            String productName, String sectionCode, String batchNumber, String unitCode, BigDecimal quantity) {}

    public record AdjustmentResponse(
            UUID id, String number, UUID warehouseId, String comment, StockAdjustment.Status status,
            UUID createdBy, OffsetDateTime createdAt, UUID approvedBy, OffsetDateTime approvedAt,
            UUID confirmedBy, OffsetDateTime confirmedAt, String cancelReason,
            List<AdjustmentItemResponse> items) {}
}
