// StockAdjustment.java
package ru.warehouse.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Документ корректировки остатка по недостачам (уменьшение). Одобрение обязательно, одобряющий не автор. */
@Entity
@Table(name = "stock_adjustments")
@Getter @Setter
public class StockAdjustment {
    public enum Status { DRAFT, CONFIRMED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @Column(nullable = false, unique = true, length = 50) private String number;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(nullable = false) private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private OffsetDateTime approvedAt;
    @Column(name = "confirmed_by") private UUID confirmedBy;
    @Column(name = "confirmed_at") private OffsetDateTime confirmedAt;
    @Column(name = "cancelled_by") private UUID cancelledBy;
    @Column(name = "cancel_reason") private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "adjustment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockAdjustmentItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
