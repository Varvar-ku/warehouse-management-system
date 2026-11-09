// Stock.java
package ru.warehouse.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock")
@Getter @Setter
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "section_id", nullable = false) private UUID sectionId;
    @Column(name = "section_code", nullable = false, length = 50) private String sectionCode;

    /** Снимок признака секции "Нераспределено": из такой секции резервировать нельзя. */
    @Column(name = "unassigned_zone", nullable = false)
    private boolean unassignedZone;

    /** Секция BLOCKED или ARCHIVED (по событию из warehouse-service): остаток есть, резервировать нельзя. */
    @Column(name = "section_blocked", nullable = false)
    private boolean sectionBlocked;

    /**
     * Строка «на проверке»: по ней зафиксирована недостача (см. StockShortage), решение за кладовщиком.
     * Остаток физически числится, но резервировать и списывать его нельзя, пока недостача не закрыта.
     */
    @Column(name = "on_hold", nullable = false)
    private boolean onHold;

    @Column(name = "hold_since")
    private OffsetDateTime holdSince;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(name = "unit_code", nullable = false, length = 20) private String unitCode;

    /** Физический остаток. */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    /** Доступный остаток = физический − зарезервированный (разд. 5.4 ТЗ). */
    public BigDecimal available() {
        return quantity.subtract(reservedQuantity);
    }
}