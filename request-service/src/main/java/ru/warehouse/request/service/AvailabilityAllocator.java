// AvailabilityAllocator.java
package ru.warehouse.request.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Делит доступное количество номенклатуры между позициями заявки по порядку строк
 * (одна номенклатура может быть в нескольких позициях). Чистая логика без БД.
 */
final class AvailabilityAllocator {
    private AvailabilityAllocator() {}

    /** productIds и requested одного размера и порядка; результат: сколько можно подтвердить по каждой позиции. */
    static List<BigDecimal> allocate(List<UUID> productIds, List<BigDecimal> requested, Map<UUID, BigDecimal> available) {
        Map<UUID, BigDecimal> remaining = new HashMap<>(available);
        List<BigDecimal> approved = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            BigDecimal left = remaining.getOrDefault(productIds.get(i), BigDecimal.ZERO).max(BigDecimal.ZERO);
            BigDecimal take = requested.get(i).min(left);
            remaining.put(productIds.get(i), left.subtract(take));
            approved.add(take);
        }
        return approved;
    }
}