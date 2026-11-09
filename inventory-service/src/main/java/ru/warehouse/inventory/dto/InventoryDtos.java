package ru.warehouse.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.warehouse.inventory.client.CatalogContract.CatalogAttribute;
import ru.warehouse.inventory.entity.PlacementTask;
import ru.warehouse.inventory.entity.Receipt;
import ru.warehouse.inventory.entity.ReceiptItem;
import ru.warehouse.inventory.entity.StockMovement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InventoryDtos {
    private InventoryDtos() {}

    // ================= запросы =================

    public record NumericValueDto(@NotNull BigDecimal value, @Size(max = 20) String unitCode) {}

    /** Уточнения характеристик партии, которые перекрывают данные каталога (например, фактическая длина). */
    public record CharacteristicsDto(@Size(max = 150) String material, Map<String, @Valid NumericValueDto> numeric) {}

    public record ReceiptItemRequest(
            @NotNull UUID productId,
            @NotNull @Positive @Digits(integer = 14, fraction = 4) BigDecimal quantity,
            @Size(max = 100) String batchNumber,
            OffsetDateTime expiryAt,
            String comment,
            @Valid CharacteristicsDto characteristics) {}

    public record CreateReceiptRequest(
            @NotNull UUID warehouseId,
            @NotBlank @Size(max = 255) String supplierName,
            @Size(max = 100) String externalDocumentNumber,
            LocalDate documentDate,   // дата накладной поставщика (справочно); дату приёмки назначает сервер
            @NotEmpty @Size(max = 200) List<@Valid ReceiptItemRequest> items) {}

    /** PATCH только для DRAFT. null = «не менять». Если передан items, список позиций заменяется целиком. */
    public record UpdateReceiptRequest(
            @Pattern(regexp = ".*\\S.*", message = "не может быть пустым") @Size(max = 255) String supplierName,
            @Size(max = 100) String externalDocumentNumber,
            LocalDate documentDate,
            @Size(max = 200) List<@Valid ReceiptItemRequest> items) {}

    /** Решение кладовщика: выбрать другую допустимую секцию (или подтвердить зону «Нераспределено»). */
    public record ItemDecision(@NotNull UUID itemId, @NotNull UUID targetSectionId) {}

    public record ConfirmReceiptRequest(List<@Valid ItemDecision> decisions) {}

    public record CreatePlacementTaskRequest(UUID assigneeId) {}

    public record CancelRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record ConfirmPlacementRequest(
            @NotNull UUID sectionId,
            @NotNull @Positive @Digits(integer = 14, fraction = 4) BigDecimal quantity,
            @Size(max = 1000) String comment) {}

    /** REJECT_LINE (B4): позиция не размещена (недостача, брак, потеря): закрывается с нулевым приходом. */
    public enum ResolveDecision { ACCEPT_ACTUAL, RETURN_TO_PLACEMENT, REJECT_LINE }

    public record ResolveRequest(
            @NotNull ResolveDecision decision,
            UUID newTargetSectionId,
            @NotBlank @Size(max = 1000) String comment) {}

    // ================= снимок партии (хранится в БД как JSON) =================

    public record BatchSnapshot(String sku, String name, String productTypeCode, String unitCode,
                                List<CatalogAttribute> catalogAttributes, CharacteristicsDto overrides) {}

    // ================= ответы =================

    public record RecommendationInfo(UUID sectionId, String sectionCode, String strategy, UUID ruleId,
                                     Integer ruleVersion, String ruleName, OffsetDateTime calculatedAt,
                                     List<String> notes) {}

    public record ReceiptItemResponse(
            UUID id, int lineNo, UUID productId, String productSku, String productName, String unitCode,
            BigDecimal quantity, String batchNumber, OffsetDateTime expiryAt, String comment,
            RecommendationInfo recommendation,
            UUID targetSectionId, String targetSectionCode, ReceiptItem.TargetDecision targetDecision,
            ReceiptItem.PlacementStatus placementStatus,
            UUID placedSectionId, String placedSectionCode, BigDecimal placedQuantity,
            String discrepancyComment, UUID batchId) {}

    public record ReceiptResponse(
            UUID id, String number, UUID warehouseId, String supplierName, String externalDocumentNumber,
            Receipt.Status status, OffsetDateTime receivedAt, LocalDate documentDate,
            UUID createdBy, OffsetDateTime createdAt,
            OffsetDateTime confirmedAt, OffsetDateTime completedAt, String cancelReason,
            List<ReceiptItemResponse> items) {}

    public record ReceiptSummary(UUID id, String number, UUID warehouseId, String supplierName,
                                 String externalDocumentNumber, Receipt.Status status,
                                 OffsetDateTime receivedAt, LocalDate documentDate, int itemsCount) {}

    public record CandidateDto(UUID sectionId, String sectionCode, BigDecimal freeCapacity) {}

    /** Результат расчёта по позиции для кладовщика: рекомендация, основание и допустимые альтернативы. */
    public record ItemRecommendationResponse(
            UUID itemId, int lineNo, String status, String strategy, UUID sectionId, String sectionCode,
            UUID ruleId, Integer ruleVersion, String ruleName, OffsetDateTime calculatedAt,
            boolean requiresStorekeeperAction, List<String> notes, List<CandidateDto> candidates) {}

    public record TaskItemResponse(
            UUID itemId, int lineNo, UUID productId, String productSku, String productName, String unitCode,
            BigDecimal quantity, UUID targetSectionId, String targetSectionCode,
            ReceiptItem.PlacementStatus placementStatus, String placedSectionCode, BigDecimal placedQuantity,
            String discrepancyComment) {}

    public record PlacementTaskResponse(
            UUID id, UUID receiptId, String receiptNumber, UUID warehouseId, PlacementTask.Status status,
            UUID assigneeId, OffsetDateTime createdAt, OffsetDateTime completedAt,
            List<TaskItemResponse> items) {}

    public record StockResponse(
            UUID id, UUID warehouseId, UUID productId, String productSku, String productName,
            UUID sectionId, String sectionCode, UUID batchId, String batchNumber, String unitCode,
            BigDecimal quantity, BigDecimal reservedQuantity, BigDecimal availableQuantity,
            boolean onHold) {}

    public record MovementResponse(
            UUID id, StockMovement.Type movementType, UUID warehouseId, UUID productId, UUID batchId,
            UUID fromSectionId, String fromSectionCode, UUID toSectionId, String toSectionCode,
            BigDecimal quantity, String unitCode, String documentType, UUID documentId,
            UUID performedBy, OffsetDateTime performedAt, String comment) {}
}