// StockRepository.java
package ru.warehouse.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.inventory.entity.Stock;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public interface StockRepository extends JpaRepository<Stock, UUID>, JpaSpecificationExecutor<Stock> {

    /**
     * Граница годности: начало текущих суток. Срок "годен до" включительно, поэтому партия
     * с expiryAt >= границы ещё годна (годна весь последний день срока).
     * Сутки считаются в часовом поясе сервера.
     */
    static OffsetDateTime expiryCutoff(OffsetDateTime now) {
        ZonedDateTime z = now.atZoneSameInstant(ZoneId.systemDefault());
        return z.toLocalDate().atStartOfDay(z.getZone()).toOffsetDateTime();
    }

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

    /**
     * Итоги для проверки наличия: только то, что можно зарезервировать
     * (партия годна на дату cutoff, секция не "Нераспределено", строка не "на проверке" из-за недостачи).
     * Товар без такого остатка в результат не попадает (вызывающий код считает его нулевым).
     */
    @Query("select s.productId as productId, sum(s.quantity) as total, sum(s.reservedQuantity) as reserved "
            + "from Stock s join s.batch b "
            + "where s.warehouseId = :warehouseId and s.productId in :productIds "
            + "and s.unassignedZone = false and s.sectionBlocked = false and s.onHold = false "
            + "and (b.expiryAt is null or b.expiryAt >= :cutoff) "
            + "group by s.productId")
    List<ProductTotals> totalsAt(@Param("warehouseId") UUID warehouseId,
                                 @Param("productIds") Collection<UUID> productIds,
                                 @Param("cutoff") OffsetDateTime cutoff);

    default List<ProductTotals> totals(UUID warehouseId, Collection<UUID> productIds) {
        return totalsAt(warehouseId, productIds, expiryCutoff(OffsetDateTime.now()));
    }

    /**
     * Кандидаты на резерв: есть свободное количество, партия годна, секция не "Нераспределено", строка не "на проверке".
     * Порядок: раньше истекает срок годности, затем раньше поступила партия (FIFO), затем код секции.
     * Блокировки нет: строки блокируются отдельно (lockAll), чтобы всегда брать их в порядке id.
     */
    @Query("select s.id from Stock s join s.batch b "
            + "where s.warehouseId = :warehouseId and s.productId = :productId "
            + "and s.quantity > s.reservedQuantity and s.unassignedZone = false and s.sectionBlocked = false and s.onHold = false "
            + "and (b.expiryAt is null or b.expiryAt >= :cutoff) "
            + "order by b.expiryAt asc nulls last, b.receivedAt asc, s.sectionCode asc, s.id asc")
    List<UUID> findAvailableIdsOrderedAt(@Param("warehouseId") UUID warehouseId,
                                         @Param("productId") UUID productId,
                                         @Param("cutoff") OffsetDateTime cutoff);

    default List<UUID> findAvailableIdsOrdered(UUID warehouseId, UUID productId) {
        return findAvailableIdsOrderedAt(warehouseId, productId, expiryCutoff(OffsetDateTime.now()));
    }

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

    /** Физически занято в секции в указанной единице (для проверки вместимости под блокировкой секции). */
    @Query("select coalesce(sum(s.quantity), 0) from Stock s where s.sectionId = :sectionId and s.unitCode = :unitCode")
    BigDecimal sectionUsed(@Param("sectionId") UUID sectionId, @Param("unitCode") String unitCode);

    @Query("select count(s) from Stock s where s.sectionId = :sectionId and s.quantity > 0")
    long countOccupied(@Param("sectionId") UUID sectionId);

    @Query("select coalesce(sum(s.reservedQuantity), 0) from Stock s where s.sectionId = :sectionId")
    BigDecimal sectionReserved(@Param("sectionId") UUID sectionId);

    /** Событие section-status: идемпотентная установка признака на всех строках секции. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Stock s set s.sectionBlocked = :blocked where s.sectionId = :sectionId")
    int updateSectionBlocked(@Param("sectionId") UUID sectionId, @Param("blocked") boolean blocked);

    /** Партию подгружаем вместе со списком (артикул и название), чтобы не было N+1. */
    @Override
    @EntityGraph(attributePaths = "batch")
    Page<Stock> findAll(Specification<Stock> spec, Pageable pageable);
}