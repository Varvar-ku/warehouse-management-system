// PickingTaskWorker.java
package ru.warehouse.request.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "picking_task_workers")
@Getter @Setter
public class PickingTaskWorker {
    public enum Role { PRIMARY, WORKER }
    public enum Status { ASSIGNED, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED, REASSIGNED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private PickingTask task;

    @Column(name = "worker_id", nullable = false) private UUID workerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.WORKER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ASSIGNED;

    @Column(name = "assigned_at", nullable = false) private OffsetDateTime assignedAt = OffsetDateTime.now();
    @Column(name = "started_at") private OffsetDateTime startedAt;
    @Column(name = "completed_at") private OffsetDateTime completedAt;
    private String comment;

    /** Работает над заданием: назначен и не снят. */
    public boolean isActive() {
        return status == Status.ASSIGNED || status == Status.ACCEPTED || status == Status.IN_PROGRESS;
    }

    /** Имеет доступ к заданию: действующий или уже завершивший свою часть. */
    public boolean isParticipating() {
        return isActive() || status == Status.COMPLETED;
    }
}
