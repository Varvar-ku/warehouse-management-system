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
import ru.warehouse.inventory.entity.Stock;
import ru.warehouse.inventory.entity.StockMovement;
import ru.warehouse.inventory.repository.StockMovementRepository;
import ru.warehouse.inventory.repository.StockRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Остатки и журнал движений (чтение). */
@Service
@RequiredArgsConstructor
public class StockQueryService {
    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;

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
        if (onlyAvailable) { // физический остаток минус резерв > 0
            spec = spec.and((root, query, cb) -> cb.greaterThan(
                    cb.diff(root.<BigDecimal>get("quantity"), root.<BigDecimal>get("reservedQuantity")), BigDecimal.ZERO));
        }
        return PageResponse.from(stockRepository.findAll(spec, pageable).map(InventoryMapper::toResponse));
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