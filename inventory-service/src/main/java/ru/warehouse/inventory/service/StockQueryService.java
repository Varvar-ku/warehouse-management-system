// StockQueryService.java
package ru.warehouse.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.inventory.dto.InventoryDtos.MovementResponse;
import ru.warehouse.inventory.dto.InventoryDtos.StockResponse;
import ru.warehouse.inventory.dto.SectionUsageResponse;
import ru.warehouse.inventory.entity.Receipt;
import ru.warehouse.inventory.entity.ReceiptItem;
import ru.warehouse.inventory.entity.Stock;
import ru.warehouse.inventory.entity.StockMovement;
import ru.warehouse.inventory.repository.ReceiptItemRepository;
import ru.warehouse.inventory.repository.StockMovementRepository;
import ru.warehouse.inventory.repository.StockRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Остатки и журнал движений (чтение). */
@Service
@RequiredArgsConstructor
public class StockQueryService {
    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final ReceiptItemRepository itemRepository;

    @Transactional(readOnly = true)
    public PageResponse<StockResponse> stock(UUID warehouseId, UUID sectionId, UUID productId,
                                             boolean onlyAvailable, boolean includeZero, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("sectionCode").and(Sort.by("id")));
        Specification<Stock> spec = (root, query, cb) -> cb.conjunction();
        if (warehouseId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("warehouseId"), warehouseId));
        if (sectionId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("sectionId"), sectionId));
        if (productId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("productId"), productId));
        if (!includeZero) {
            spec = spec.and((root, query, cb) -> cb.greaterThan(root.<BigDecimal>get("quantity"), BigDecimal.ZERO));
        }
        if (onlyAvailable) { // доступно к резерву: свободный остаток > 0, партия годна, секция не "Нераспределено"
            OffsetDateTime cutoff = StockRepository.expiryCutoff(OffsetDateTime.now());
            spec = spec.and((root, query, cb) -> cb.greaterThan(
                    cb.diff(root.<BigDecimal>get("quantity"), root.<BigDecimal>get("reservedQuantity")), BigDecimal.ZERO));
            spec = spec.and((root, query, cb) -> cb.isFalse(root.<Boolean>get("unassignedZone")));
            spec = spec.and((root, query, cb) -> cb.isFalse(root.<Boolean>get("sectionBlocked")));
            spec = spec.and((root, query, cb) -> cb.isFalse(root.<Boolean>get("onHold")));
            spec = spec.and((root, query, cb) -> {
                var batch = root.join("batch");
                return cb.or(cb.isNull(batch.get("expiryAt")),
                        cb.greaterThanOrEqualTo(batch.<OffsetDateTime>get("expiryAt"), cutoff));
            });
        }
        return PageResponse.from(stockRepository.findAll(spec, pageable).map(InventoryMapper::toResponse));
    }

    /** Занятость секции для warehouse-service (запрет архивации секции с товаром). */
    @Transactional(readOnly = true)
    public SectionUsageResponse sectionUsage(UUID sectionId) {
        long occupiedRows = stockRepository.countOccupied(sectionId);
        BigDecimal reserved = stockRepository.sectionReserved(sectionId);
        long pending = itemRepository.countOpenPlacements(sectionId,
                List.of(Receipt.Status.RECEIVING, Receipt.Status.PLACEMENT),
                ReceiptItem.PlacementStatus.PENDING, ReceiptItem.PlacementStatus.DISCREPANCY);
        return new SectionUsageResponse(sectionId, occupiedRows > 0, reserved, pending, occupiedRows > 0 || pending > 0);
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> movements(UUID warehouseId, UUID productId, UUID sectionId,
                                                    StockMovement.Type type, String documentType, UUID documentId,
                                                    OffsetDateTime from, OffsetDateTime to, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "performedAt").and(Sort.by("id")));
        Specification<StockMovement> spec = (root, query, cb) -> cb.conjunction();
        if (warehouseId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("warehouseId"), warehouseId));
        if (productId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("productId"), productId));
        if (sectionId != null) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.equal(root.get("fromSectionId"), sectionId), cb.equal(root.get("toSectionId"), sectionId)));
        }
        if (type != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("movementType"), type));
        if (documentType != null && !documentType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("documentType"), documentType.trim().toUpperCase()));
        }
        if (documentId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("documentId"), documentId));
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("performedAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.<OffsetDateTime>get("performedAt"), to));
        }
        return PageResponse.from(movementRepository.findAll(spec, pageable).map(InventoryMapper::toResponse));
    }
}