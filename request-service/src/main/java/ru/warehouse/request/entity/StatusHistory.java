// StatusHistory.java
package ru.warehouse.request.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Неизменяемая запись об изменении статуса (БД запрещает UPDATE и DELETE). */
@Entity
@Table(name = "request_status_history")
@Getter @Setter
public class StatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false) private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 40)
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 40)
    private RequestStatus toStatus;

    @Column(name = "actor_id", nullable = false) private UUID actorId;
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}