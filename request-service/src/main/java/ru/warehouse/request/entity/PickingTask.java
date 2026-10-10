// PickingTask.java
package ru.warehouse.request.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "picking_tasks")
@Getter @Setter
public class PickingTask {
    public enum Status { NEW, IN_PROGRESS, COMPLETED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @Column(nullable = false, unique = true, length = 50) private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.NEW;

    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "completed_at") private OffsetDateTime completedAt;
    @Column(name = "completed_by") private UUID completedBy;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("assignedAt ASC")
    private List<PickingTaskWorker> workers = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sectionCode ASC")
    private List<PickingItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    /** Задание ещё в работе (можно назначать, собирать, завершать). */
    public boolean isOpen() {
        return status == Status.NEW || status == Status.IN_PROGRESS;
    }

    /** Отклонение при отмене заявки: действующие исполнители снимаются. */
    public void reject() {
        status = Status.REJECTED;
        for (PickingTaskWorker w : workers) {
            if (w.isActive()) w.setStatus(PickingTaskWorker.Status.CANCELLED);
        }
    }
}