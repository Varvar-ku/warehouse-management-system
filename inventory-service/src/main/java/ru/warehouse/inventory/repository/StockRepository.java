// StockRepository.java
package ru.warehouse.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.inventory.entity.Stock;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public interface StockRepository extends JpaRepository<Stock, UUID>, JpaSpecificationExecutor<Stock> {

    /** Проекции для расчёта занятости секций. */
    interface UsageRow {
        UUID getSectionId();
        String getUnitCode();
        BigDecimal getQty();
    }

    interface SectionProductRow {
        UUID getSectionId();
        UUID getProductId();
    }

    /** Итоги по номенклатуре склада (для проверки наличия). */
    interface ProductTotals {
        UUID getProductId();
        BigDecimal getTotal();
        BigDecimal getReserved();
    }

    @Query("select s.productId as productId, sum(s.quantity) as total, sum(s.reservedQuantity) as reserved "
            + "from Stock s where s.warehouseId = :warehouseId and s.productId in :productIds group by s.productId")
    List<ProductTotals> totals(@Param("warehouseId") UUID warehouseId, @Param("productIds") Collection<UUID> productIds);

    /**
     * Остатки номенклатуры со свободным количеством в порядке выбора для резерва:
     * раньше истекает срок годности, затем раньше поступила партия (FIFO), затем код секции.
     * Блокировки нет: строки блокируются отдельно (lockAll), чтобы всегда брать их в порядке id.
     */
    @Query("select s.id from Stock s join s.batch b "
            + "where s.warehouseId = :warehouseId and s.productId = :productId and s.quantity > s.reservedQuantity "
            + "order by b.expiryAt asc nulls last, b.receivedAt asc, s.sectionCode asc, s.id asc")
    List<UUID> findAvailableIdsOrdered(@Param("warehouseId") UUID warehouseId, @Param("productId") UUID productId);

    /** FOR UPDATE строго по возрастанию id: два процесса, блокирующие пересекающиеся наборы, не создают взаимной блокировки. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.id in :ids order by s.id")
    List<Stock> lockAllByIdIn(@Param("ids") Collection<UUID> ids);

    default Map<UUID, Stock> lockAll(Collection<UUID> ids) {
        Map<UUID, Stock> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        for (Stock s : lockAllByIdIn(ids)) result.put(s.getId(), s);
        return result;
    }

    @Query("select s.sectionId as sectionId, s.unitCode as unitCode, sum(s.quantity) as qty "
            + "from Stock s where s.warehouseId = :warehouseId and s.quantity > 0 "
            + "group by s.sectionId, s.unitCode")
    List<UsageRow> usage(@Param("warehouseId") UUID warehouseId);

    @Query("select distinct s.sectionId as sectionId, s.productId as productId "
            + "from Stock s where s.warehouseId = :warehouseId and s.quantity > 0")
    List<SectionProductRow> products(@Param("warehouseId") UUID warehouseId);

    /** Партию подгружаем вместе со списком (артикул и название), чтобы не было N+1. */
    @Override
    @EntityGraph(attributePaths = "batch")
    Page<Stock> findAll(Specification<Stock> spec, Pageable pageable);
}