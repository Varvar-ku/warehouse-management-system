package ru.warehouse.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.warehouse.inventory.entity.Issue;
import ru.warehouse.inventory.entity.Reservation;
import ru.warehouse.inventory.entity.WriteOff;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class OperationDtos {
    private OperationDtos() {}

    // ================= резервы =================

    public record AvailabilityItemRequest(
            @NotNull UUID productId,
            @NotNull @Positive @Digits(integer = 14, fraction = 4) BigDecimal quantity) {}

    public record AvailabilityRequest(
            @NotNull UUID warehouseId,
            @NotEmpty @Size(max = 200) List<@Valid AvailabilityItemRequest> items) {}

    /** Требуемое, доступное и недостающее количество (разд. 11 ТЗ). */
    public record AvailabilityItem(UUID productId, BigDecimal requested, BigDecimal available, BigDecimal shortage) {}

    public record ReserveItemRequest(
            @NotNull UUID requestItemId,
            @NotNull UUID productId,
            @NotNull @Positive @Digits(integer = 14, fraction = 4) BigDecimal quantity) {}

    public record ReserveRequest(
            @NotNull UUID requestId,
            @NotNull UUID warehouseId,
            Boolean allowPartial,   // Boolean, а не boolean: в Jackson 3 отсутствующее примитивное поле даёт ошибку разбора
            @NotEmpty @Size(max = 200) List<@Valid ReserveItemRequest> items) {

        public ReserveRequest {
            if (allowPartial == null) allowPartial = false;   // поле не передано = false (резервировать только целиком)
        }
    }
    public record Allocation(UUID reservationId, UUID stockId, UUID sectionId, String sectionCode,
                             UUID batchId, String batchNumber, BigDecimal quantity) {}

    public record ReserveItemResult(UUID requestItemId, UUID productId, BigDecimal requested, BigDecimal reserved,
                                    BigDecimal shortage, List<Allocation> allocations) {}

    public record ReserveResponse(UUID requestId, boolean fullyReserved, List<ReserveItemResult> items) {}

    /**
     * Часть резерва, которую не удалось собрать (недобор). При снятии резерва или выдаче по такому резерву
     * регистрируется недостача, а строка остатка блокируется до решения кладовщика (B1).
     */
    public record ShortageMark(
            @NotNull UUID reservationId,
            @NotNull @Positive @Digits(integer = 14, fraction = 4) BigDecimal quantity) {}

    public record ReleaseRequest(@NotNull UUID requestId, List<UUID> requestItemIds,
                                 List<@Valid ShortageMark> shortages) {
        public ReleaseRequest(UUID requestId, List<UUID> requestItemIds) {
            this(requestId, requestItemIds, null);
        }
    }

    public record ReleaseResponse(UUID requestId, int releasedReservations, BigDecimal releasedQuantity) {}

    public record ReservationResponse(UUID id, UUID requestId, UUID requestItemId, UUID stockId,
                                      UUID sectionId, String sectionCode, UUID batchId, String batchNumber,
                                      String productSku, BigDecimal quantity, BigDecimal consumedQuantity,
                                      BigDecimal releasedQuantity, Reservation.Status status,
                                      OffsetDateTime createdAt, OffsetDateTime closedAt) {}
    // ================= выдача =================

    public record IssueItemRequest(
            @NotNull UUID requestItemId,
            @NotNull UUID productId,
            @NotNull @Positive @Digits(integer = 14, fraction = 4) BigDecimal quantity) {}   // фактически выдаваемое

    public record CreateIssueRequest(
            @NotNull UUID requestId,
            @NotNull UUID warehouseId,
            @NotNull UUID issuedTo,
            @Size(max = 1000) String comment,
            @NotEmpty @Size(max = 200) List<@Valid IssueItemRequest> items,
            List<@Valid ShortageMark> shortages) {   // недобор по резервам: откуда фактически не собрано

        public CreateIssueRequest(UUID requestId, UUID warehouseId, UUID issuedTo, String comment,
                                  List<IssueItemRequest> items) {
            this(requestId, warehouseId, issuedTo, comment, items, null);
        }
    }

    public record IssueItemResponse(UUID id, UUID requestItemId, UUID productId, String productSku,
                                    String productName, UUID stockId, String sectionCode, String batchNumber,
                                    String unitCode, BigDecimal quantity) {}

    public record IssueResponse(UUID id, String number, UUID warehouseId, UUID requestId, UUID issuedTo,
                                UUID issuedBy, Issue.Status status, OffsetDateTime createdAt,
                                OffsetDateTime issuedAt, String comment, List<IssueItemResponse> items) {}

    // ================= списание =================

    public record WriteOffItemRequest(
            @NotNull UUID stockId,
            @NotNull @Positive @Digits(integer = 14, fraction = 4) BigDecimal quantity) {}

    public record CreateWriteOffRequest(
            @NotNull UUID warehouseId,
            @NotNull WriteOff.Reason reasonCode,
            @Size(max = 1000) String comment,
            @NotEmpty @Size(max = 200) List<@Valid WriteOffItemRequest> items) {}

    public record WriteOffItemResponse(UUID id, UUID stockId, UUID productId, String productSku,
                                       String productName, String sectionCode, String batchNumber,
                                       String unitCode, BigDecimal quantity) {}

    public record WriteOffResponse(UUID id, String number, UUID warehouseId, WriteOff.Reason reasonCode,
                                   String comment, WriteOff.Status status, boolean approvalRequired,
                                   UUID approvedBy, OffsetDateTime approvedAt, UUID createdBy,
                                   OffsetDateTime createdAt, UUID confirmedBy, OffsetDateTime confirmedAt,
                                   List<WriteOffItemResponse> items) {}
}
