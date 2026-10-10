// PickingReassignment.java
package ru.warehouse.request.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Запись журнала переназначений (кто, у кого, кому, сколько, почему). */
@Entity
@Table(name = "picking_reassignments")
@Getter @Setter
public class PickingReassignment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false) private UUID taskId;
    @Column(name = "picking_item_id", nullable = false) private UUID pickingItemId;
    @Column(name = "request_item_id", nullable = false) private UUID requestItemId;
    @Column(name = "from_worker_id", nullable = false) private UUID fromWorkerId;
    @Column(name = "to_worker_id", nullable = false) private UUID toWorkerId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "actor_id", nullable = false) private UUID actorId;
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}