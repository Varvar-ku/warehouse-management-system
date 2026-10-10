// PickingItem.java
package ru.warehouse.request.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Строка комплектации: часть резерва (остаток, секция, партия), назначенная одному работнику или пока свободная. */
@Entity
@Table(name = "picking_items")
@Getter @Setter
public class PickingItem {
    public enum Status { PENDING, PICKED, SHORTAGE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private PickingTask task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_item_id", nullable = false)
    private RequestItem requestItem;

    @Column(name = "reservation_id", nullable = false) private UUID reservationId;
    @Column(name = "stock_id", nullable = false) private UUID stockId;
    @Column(name = "section_id", nullable = false) private UUID sectionId;
    @Column(name = "section_code", nullable = false, length = 50) private String sectionCode;
    @Column(name = "batch_id") private UUID batchId;
    @Column(name = "batch_number", length = 100) private String batchNumber;

    @Column(name = "requested_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal requestedQuantity;

    @Column(name = "picked_quantity", precision = 18, scale = 4)
    private BigDecimal pickedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "assigned_worker_id") private UUID assignedWorkerId;
    private String comment;
    @Column(name = "picked_at") private OffsetDateTime pickedAt;

    /** Строка обработана: собрана полностью или оформлена недостача. */
    public boolean processed() {
        return status != Status.PENDING;
    }
}