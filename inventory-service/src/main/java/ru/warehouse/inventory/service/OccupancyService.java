// OccupancyService.java
package ru.warehouse.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.inventory.client.WarehouseContract.Occupancy;
import ru.warehouse.inventory.entity.Receipt;
import ru.warehouse.inventory.entity.ReceiptItem;
import ru.warehouse.inventory.repository.ReceiptItemRepository;
import ru.warehouse.inventory.repository.StockRepository;
import java.math.BigDecimal;
import java.util.*;

/**
 * Занятость секций склада для движка подбора: физические остатки плюс запланированные,
 * но ещё не размещённые позиции других поступлений (иначе две приёмки займут одно место).
 */
@Service
@RequiredArgsConstructor
public class OccupancyService {
    private static final List<Receipt.Status> IN_PROGRESS = List.of(Receipt.Status.RECEIVING, Receipt.Status.PLACEMENT);

    private final StockRepository stockRepository;
    private final ReceiptItemRepository itemRepository;

    @Transactional(readOnly = true)
    public List<Occupancy> forWarehouse(UUID warehouseId, UUID excludeReceiptId) {
        Map<UUID, Map<String, BigDecimal>> used = new HashMap<>();
        Map<UUID, Set<UUID>> products = new HashMap<>();

        stockRepository.usage(warehouseId).forEach(row -> addUsed(used, row.getSectionId(), row.getUnitCode(), row.getQty()));
        stockRepository.products(warehouseId).forEach(row -> addProduct(products, row.getSectionId(), row.getProductId()));

        itemRepository.pendingUsage(warehouseId, IN_PROGRESS, ReceiptItem.PlacementStatus.PENDING, excludeReceiptId)
                .forEach(row -> addUsed(used, row.getSectionId(), row.getUnitCode(), row.getQty()));
        itemRepository.pendingProducts(warehouseId, IN_PROGRESS, ReceiptItem.PlacementStatus.PENDING, excludeReceiptId)
                .forEach(row -> addProduct(products, row.getSectionId(), row.getProductId()));

        Set<UUID> sectionIds = new HashSet<>(used.keySet());
        sectionIds.addAll(products.keySet());
        return sectionIds.stream()
                .map(id -> new Occupancy(id, used.getOrDefault(id, Map.of()), products.getOrDefault(id, Set.of())))
                .toList();
    }

    private static void addUsed(Map<UUID, Map<String, BigDecimal>> used, UUID sectionId, String unit, BigDecimal qty) {
        used.computeIfAbsent(sectionId, k -> new HashMap<>()).merge(unit, qty, BigDecimal::add);
    }

    private static void addProduct(Map<UUID, Set<UUID>> products, UUID sectionId, UUID productId) {
        products.computeIfAbsent(sectionId, k -> new HashSet<>()).add(productId);
    }
}