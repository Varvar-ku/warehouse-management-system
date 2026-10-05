// Reservation.java
package ru.warehouse.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Резерв конкретного остатка (партии в секции) под позицию заявки. */
@Entity
@Table(name = "reservations")
@Getter @Setter
public class Reservation {
    public enum Status { ACTIVE, RELEASED, CONSUMED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "request_item_id", nullable = false) private UUID requestItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "consumed_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal consumedQuantity = BigDecimal.ZERO;

    @Column(name = "released_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal releasedQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "closed_at") private OffsetDateTime closedAt;
    @Column(name = "closed_by") private UUID closedBy;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
