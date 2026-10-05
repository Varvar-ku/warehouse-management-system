// PlacementTask.java
package ru.warehouse.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "placement_tasks")
@Getter @Setter
public class PlacementTask {
    public enum Status { OPEN, COMPLETED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false, unique = true)
    private Receipt receipt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    /** null = задание видят все работники склада. */
    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}