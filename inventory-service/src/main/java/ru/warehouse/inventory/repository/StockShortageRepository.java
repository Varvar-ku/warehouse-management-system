// StockShortageRepository.java
package ru.warehouse.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.inventory.entity.StockShortage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockShortageRepository extends JpaRepository<StockShortage, UUID>,
        JpaSpecificationExecutor<StockShortage> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockShortage s where s.id = :id")
    Optional<StockShortage> findByIdForUpdate(@Param("id") UUID id);

    /** FOR UPDATE по возрастанию id (единый порядок блокировок). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockShortage s where s.id in :ids order by s.id")
    List<StockShortage> lockAllByIdIn(@Param("ids") Collection<UUID> ids);

    boolean existsByStockIdAndStatus(UUID stockId, StockShortage.Status status);

    @Query(value = "select nextval('shortage_number_seq')", nativeQuery = true)
    long nextNumber();

    /** Строку остатка и партию подгружаем вместе со списком (артикул, секция), чтобы не было N+1. */
    @Override
    @EntityGraph(attributePaths = {"stock", "stock.batch"})
    Page<StockShortage> findAll(Specification<StockShortage> spec, Pageable pageable);
}
