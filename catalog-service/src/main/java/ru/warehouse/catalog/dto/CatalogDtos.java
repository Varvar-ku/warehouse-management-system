package ru.warehouse.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.warehouse.catalog.entity.AttributeDataType;
import ru.warehouse.catalog.entity.UnitDimension;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {}

    // ---------- Номенклатура ----------
    public record AttributeValueRequest(
            @NotBlank @Size(max = 100) String attributeCode,
            String valueString,       // для STRING и REFERENCE
            BigDecimal valueNumber,   // для NUMBER
            Boolean valueBoolean,     // для BOOLEAN
            UUID valueUnitId) {}      // необязательно, для NUMBER

    public record CreateProductRequest(
            @NotBlank @Size(max = 100) String sku,
            @NotBlank @Size(max = 255) String name,
            @NotNull UUID productTypeId,
            @NotNull UUID unitId,
            String description,
            @Valid List<AttributeValueRequest> attributes) {}

    /**
     * PATCH: поле null = «не менять». Если передан attributes, набор атрибутов заменяется целиком.
     * SKU и единицу измерения менять нельзя: на них будут ссылаться остатки и документы.
     */
    public record UpdateProductRequest(
            @Pattern(regexp = ".*\\S.*", message = "не может быть пустым") @Size(max = 255) String name,
            UUID productTypeId,
            String description,
            @Valid List<AttributeValueRequest> attributes) {}

    public record AttributeValueResponse(
            String attributeCode, String valueString, BigDecimal valueNumber,
            Boolean valueBoolean, UUID valueUnitId,
            String valueUnitCode) {}   // код единицы значения, например "MM"; null, если единица не задана

    public record ProductResponse(
            UUID id, String sku, String name,
            UUID productTypeId, String productTypeCode,
            UUID unitId, String unitCode, boolean unitAllowFractions,
            String description, boolean active,
            List<AttributeValueResponse> attributes,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    // ---------- Справочники ----------
    public record ProductTypeRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            String description) {}

    public record ProductTypeUpdateRequest(
            @Pattern(regexp = ".*\\S.*", message = "не может быть пустым")
            @Size(max = 150) String name,
            String description,
            Boolean active) {}

    public record ProductTypeResponse(UUID id, String code, String name, String description, boolean active) {}

    public record UnitRequest(@NotBlank @Size(max = 20) String code, @NotBlank @Size(max = 50) String name,
                              Boolean allowFractions,       // null = true
                              UnitDimension dimension) {}   // null = OTHER; LENGTH/MASS/COUNT для проверки атрибутов

    public record UnitResponse(UUID id, String code, String name, boolean allowFractions, UnitDimension dimension) {}

    public record AttributeDefinitionRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull AttributeDataType dataType,
            Set<String> options,      // обязателен только для REFERENCE
            UnitDimension dimension) {}   // только для NUMBER: LENGTH или MASS (тип единицы значения), иначе null

    public record AttributeDefinitionResponse(
            UUID id, String code, String name, AttributeDataType dataType,
            boolean active, Set<String> options, UnitDimension dimension) {}

}