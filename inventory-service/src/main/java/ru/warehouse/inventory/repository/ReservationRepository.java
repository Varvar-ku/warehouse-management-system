// ReservationRepository.java
package ru.warehouse.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.inventory.entity.Reservation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    boolean existsByRequestItemIdInAndStatus(Collection<UUID> requestItemIds, Reservation.Status status);

    List<Reservation> findByRequestIdOrderByCreatedAtAscIdAsc(UUID requestId);

    List<Reservation> findByRequestItemIdAndStatusOrderByCreatedAtAscIdAsc(UUID requestItemId, Reservation.Status status);

    /** Порядок блокировок: сначала резервы, потом строки остатков (по id). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.requestId = :requestId and r.status = :status "
            + "order by r.createdAt, r.id")
    List<Reservation> lockByRequestAndStatus(@Param("requestId") UUID requestId,
                                             @Param("status") Reservation.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.requestId = :requestId and r.requestItemId in :itemIds "
            + "and r.status = :status order by r.createdAt, r.id")
    List<Reservation> lockByRequestItemsAndStatus(@Param("requestId") UUID requestId,
                                                  @Param("itemIds") Collection<UUID> itemIds,
                                                  @Param("status") Reservation.Status status);
}