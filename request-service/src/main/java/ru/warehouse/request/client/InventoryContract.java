// InventoryContract.java
package ru.warehouse.request.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Контракт inventory-service. Запросы содержат ТОЛЬКО известные ему поля (там строгий режим),
 * поля-флаги только Boolean (а не boolean).
 */
public final class InventoryContract {
    private InventoryContract() {}

    public record AvailabilityLine(UUID productId, BigDecimal quantity) {}
    public record AvailabilityRequest(UUID warehouseId, List<AvailabilityLine> items) {}
    public record AvailabilityItem(UUID productId, BigDecimal requested, BigDecimal available, BigDecimal shortage) {}

    public record ReserveLine(UUID requestItemId, UUID productId, BigDecimal quantity) {}
    public record ReserveRequest(UUID requestId, UUID warehouseId, Boolean allowPartial, List<ReserveLine> items) {}
    public record ReserveItemResult(UUID requestItemId, UUID productId, BigDecimal requested,
                                    BigDecimal reserved, BigDecimal shortage) {}
    public record ReserveResponse(UUID requestId, boolean fullyReserved, List<ReserveItemResult> items) {}

    /** Недобор по резерву: при снятии резерва или выдаче inventory регистрирует недостачу (B1). */
    public record ShortageMark(UUID reservationId, BigDecimal quantity) {}

    public record ReleaseRequest(UUID requestId, List<UUID> requestItemIds, List<ShortageMark> shortages) {
        public ReleaseRequest(UUID requestId, List<UUID> requestItemIds) {
            this(requestId, requestItemIds, null);
        }
    }
    public record ReleaseResponse(UUID requestId, int releasedReservations, BigDecimal releasedQuantity) {}

    public record ReservationDto(UUID id, UUID requestId, UUID requestItemId, UUID stockId, UUID sectionId,
                                 String sectionCode, UUID batchId, String batchNumber, String productSku,
                                 BigDecimal quantity, String status) {}

    public record IssueLine(UUID requestItemId, UUID productId, BigDecimal quantity) {}
    public record CreateIssueRequest(UUID requestId, UUID warehouseId, UUID issuedTo, String comment,
                                     List<IssueLine> items, List<ShortageMark> shortages) {
        public CreateIssueRequest(UUID requestId, UUID warehouseId, UUID issuedTo, String comment,
                                  List<IssueLine> items) {
            this(requestId, warehouseId, issuedTo, comment, items, null);
        }
    }
    public record IssueDto(UUID id, String number, UUID requestId, String status) {}
    public record CancelIssueRequest(String reason) {}
}