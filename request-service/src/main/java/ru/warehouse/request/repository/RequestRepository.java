// RequestRepository.java
package ru.warehouse.request.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.request.entity.Request;
import java.util.Optional;
import java.util.UUID;

public interface RequestRepository extends JpaRepository<Request, UUID>, JpaSpecificationExecutor<Request> {

    /** Блокировка заявки на время операции: два кладовщика не проведут один переход дважды. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Request r where r.id = :id")
    Optional<Request> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select nextval('request_number_seq')", nativeQuery = true)
    long nextNumber();
}