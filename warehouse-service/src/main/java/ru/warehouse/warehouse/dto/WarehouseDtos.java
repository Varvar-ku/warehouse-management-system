package ru.warehouse.warehouse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.warehouse.warehouse.entity.SectionStatus;
import ru.warehouse.warehouse.placement.RuleConditions;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public final class WarehouseDtos {
    private WarehouseDtos() {}

    // ---------- Склады ----------
    public record WarehouseRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            String address) {}

    /** PATCH: null = «не менять». */
    public record WarehouseUpdateRequest(
            @Pattern(regexp = ".*\\S.*", message = "не может быть пустым") @Size(max = 255) String name,
            String address,
            Boolean active) {}

    public record WarehouseResponse(UUID id, String code, String name, String address, boolean active) {}

    // ---------- Секции ----------
    /**
     * Ограничения секции одной группой. Размеры в мм. Пустые наборы = «любой».
     * В PATCH, если constraints передан, группа заменяется целиком (так можно снять границу).
     */
    public record SectionConstraints(
            @PositiveOrZero BigDecimal lengthMin, @PositiveOrZero BigDecimal lengthMax,
            @PositiveOrZero BigDecimal widthMin, @PositiveOrZero BigDecimal widthMax,
            @PositiveOrZero BigDecimal heightMin, @PositiveOrZero BigDecimal heightMax,
            @Positive BigDecimal maxCapacity,
            @Size(max = 20) String capacityUnitCode,
            Set<@NotBlank @Size(max = 50) String> allowedProductTypes,
            Set<@NotBlank @Size(max = 150) String> allowedMaterials) {}

    public record CreateSectionRequest(
            @NotNull UUID warehouseId,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 50) String sectionType,
            @Size(max = 100) String zone,
            @Size(max = 100) String rack,
            Integer priority,
            @Valid SectionConstraints constraints) {}

    public record UpdateSectionRequest(
            @Pattern(regexp = ".*\\S.*", message = "не может быть пустым") @Size(max = 255) String name,
            @Size(max = 50) String sectionType,
            @Size(max = 100) String zone,
            @Size(max = 100) String rack,
            Integer priority,
            SectionStatus status,
            @Valid SectionConstraints constraints) {}

    public record SectionResponse(UUID id, UUID warehouseId, String code, String name, String sectionType,
                                  String zone, String rack, int priority, SectionStatus status,
                                  boolean unassignedZone, SectionConstraints constraints) {}

    // ---------- Правила размещения ----------
    public record RuleRequest(
            @NotBlank @Size(max = 255) String name,
            Integer priority,
            Boolean fallback,
            @NotNull @Valid RuleConditions conditions,
            @NotNull UUID targetSectionId) {}

    /** PATCH: null = «не менять». Любое реальное изменение увеличивает версию правила. */
    public record RuleUpdateRequest(
            @Pattern(regexp = ".*\\S.*", message = "не может быть пустым") @Size(max = 255) String name,
            Integer priority,
            Boolean active,
            Boolean fallback,
            @Valid RuleConditions conditions,
            UUID targetSectionId) {}

    public record RuleResponse(UUID id, UUID warehouseId, String name, int priority, boolean active,
                               boolean fallback, int ruleVersion, RuleConditions conditions,
                               UUID targetSectionId, String targetSectionCode) {}

    /** Снимок правила для истории версий. */
    public record RuleSnapshot(int ruleVersion, String name, int priority, boolean active, boolean fallback,
                               RuleConditions conditions, UUID targetSectionId) {}

    public record RuleVersionResponse(int version, OffsetDateTime changedAt, UUID changedBy, RuleSnapshot snapshot) {}
}