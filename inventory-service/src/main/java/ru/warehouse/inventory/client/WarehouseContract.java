// WarehouseContract.java
package ru.warehouse.inventory.client;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Контракт warehouse-service. Запросы содержат ТОЛЬКО поля, известные ему
 * (там включён строгий режим: лишнее поле даёт 400).
 */
public final class WarehouseContract {
    private WarehouseContract() {}

    public record NumericValue(BigDecimal value, String unitCode) {}

    public record PlacementItem(String lineId, UUID productId, String productTypeCode, String material,
                                Map<String, NumericValue> attributes, BigDecimal quantity, String unitCode) {}

    public record Occupancy(UUID sectionId, Map<String, BigDecimal> usedByUnit, Set<UUID> productIds) {}

    public record PlacementRequest(UUID warehouseId, List<PlacementItem> items, List<Occupancy> occupancy) {}

    public record Candidate(UUID sectionId, String sectionCode, BigDecimal freeCapacity) {}

    public record PlacementResult(String lineId, String status, String strategy, UUID sectionId, String sectionCode,
                                  UUID ruleId, Integer ruleVersion, String ruleName, OffsetDateTime calculatedAt,
                                  boolean requiresStorekeeperAction, List<String> notes, List<Candidate> candidates) {}

    public record WarehouseInfo(UUID id, String code, String name, boolean active) {}

    public record SectionInfo(UUID id, UUID warehouseId, String code, String name, String status,
                              boolean unassignedZone) {}
}