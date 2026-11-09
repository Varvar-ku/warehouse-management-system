// PlacementItemFactory.java
package ru.warehouse.inventory.service;

import ru.warehouse.inventory.client.CatalogContract.CatalogAttribute;
import ru.warehouse.inventory.client.WarehouseContract.NumericValue;
import ru.warehouse.inventory.client.WarehouseContract.PlacementItem;
import ru.warehouse.inventory.dto.InventoryDtos.BatchSnapshot;
import ru.warehouse.inventory.dto.InventoryDtos.CharacteristicsDto;
import ru.warehouse.inventory.entity.ReceiptItem;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Характеристики позиции для проверки секции, собранные из снимка приёмки (без обращения к каталогу).
 * Правило то же, что при подборе: данные каталога, поверх них уточнения кладовщика.
 */
public final class PlacementItemFactory {
    private PlacementItemFactory() {}

    public static PlacementItem fromSnapshot(ReceiptItem item, BatchSnapshot snapshot, BigDecimal quantity) {
        CharacteristicsDto overrides = snapshot.overrides();
        String material = overrides == null ? null : overrides.material();
        Map<String, NumericValue> numeric = new HashMap<>();
        if (snapshot.catalogAttributes() != null) {
            for (CatalogAttribute a : snapshot.catalogAttributes()) {
                if (a.valueNumber() != null) {
                    numeric.put(a.attributeCode(), new NumericValue(a.valueNumber(), a.valueUnitCode()));
                }
                if (material == null && "MATERIAL".equals(a.attributeCode()) && a.valueString() != null) {
                    material = a.valueString();
                }
            }
        }
        if (overrides != null && overrides.numeric() != null) {
            overrides.numeric().forEach((code, v) -> numeric.put(code, new NumericValue(v.value(), v.unitCode())));
        }
        return new PlacementItem(item.getId().toString(), item.getProductId(), snapshot.productTypeCode(),
                material, numeric, quantity, item.getUnitCode());
    }
}
