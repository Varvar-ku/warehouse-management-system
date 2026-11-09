// StockShortage.java
package ru.warehouse.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Недостача по строке остатка: при выдаче часть резерва осталась неиспользованной (недобор при сборе).
 * Пока запись OPEN, строка остатка заблокирована (Stock.onHold). Закрывается решением кладовщика:
 * FOUND (товар найден, остаток верен) или WRITTEN_OFF (подтверждена документом корректировки).
 */
@Entity
@Table(name = "stock_shortages")
@Getter @Setter
public class StockShortage {
    public enum Status { OPEN, RESOLVED }
    public enum Resolution { FOUND, WRITTEN_OFF }
    /** ISSUE: обнаружена при выдаче; CANCELLATION: при снятии резерва отменённой заявки. */
    public enum Source { ISSUE, CANCELLATION }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @Column(nullable = false, unique = true, length = 50) private String number;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(name = "product_id", nullable = false) private UUID productId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "reservation_id", nullable = false) private UUID reservationId;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "request_item_id", nullable = false) private UUID requestItemId;
    @Column(name = "issue_id") private UUID issueId;   // null для источника CANCELLATION

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source = Source.ISSUE;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_code", nullable = false, length = 20) private String unitCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Resolution resolution;

    @Column(name = "resolution_comment") private String resolutionComment;
    @Column(name = "resolved_by") private UUID resolvedBy;
    @Column(name = "resolved_at") private OffsetDateTime resolvedAt;

    /** Документ корректировки, в который включена недостача (пока он не отменён). */
    @Column(name = "adjustment_id") private UUID adjustmentId;

    @Column(name = "reported_by", nullable = false, updatable = false) private UUID reportedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
