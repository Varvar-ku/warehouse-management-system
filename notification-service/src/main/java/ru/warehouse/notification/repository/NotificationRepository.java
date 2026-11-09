package ru.warehouse.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.notification.entity.Notification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientId(UUID recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadAtIsNull(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    boolean existsByEventIdAndRecipientId(UUID eventId, UUID recipientId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);
}
