// ReceiptRepository.java
package ru.warehouse.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.inventory.entity.Receipt;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID>, JpaSpecificationExecutor<Receipt> {

    /** Блокировка документа на время операции: два работника не проведут одну позицию дважды. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Receipt r where r.id = :id")
    Optional<Receipt> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select nextval('receipt_number_seq')", nativeQuery = true)
    long nextNumber();
}