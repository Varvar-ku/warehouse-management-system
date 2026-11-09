// StockAdjustmentRepository.java
package ru.warehouse.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.inventory.entity.StockAdjustment;
import java.util.Optional;
import java.util.UUID;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID>,
        JpaSpecificationExecutor<StockAdjustment> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StockAdjustment a where a.id = :id")
    Optional<StockAdjustment> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select nextval('adjustment_number_seq')", nativeQuery = true)
    long nextNumber();
}
