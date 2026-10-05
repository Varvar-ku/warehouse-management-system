// CatalogContract.java
package ru.warehouse.inventory.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Контракт ответов catalog-service (лишние поля игнорируются). */
public final class CatalogContract {
    private CatalogContract() {}

    public record CatalogAttribute(String attributeCode, String valueString, BigDecimal valueNumber,
                                   Boolean valueBoolean, UUID valueUnitId, String valueUnitCode) {}

    public record CatalogProduct(UUID id, String sku, String name, UUID productTypeId, String productTypeCode,
                                 UUID unitId, String unitCode, Boolean unitAllowFractions, boolean active,
                                 List<CatalogAttribute> attributes) {}
}