// WriteOffRepository.java
package ru.warehouse.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.inventory.entity.WriteOff;
import java.util.Optional;
import java.util.UUID;

public interface WriteOffRepository extends JpaRepository<WriteOff, UUID>, JpaSpecificationExecutor<WriteOff> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WriteOff w where w.id = :id")
    Optional<WriteOff> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select nextval('write_off_number_seq')", nativeQuery = true)
    long nextNumber();
}
