// StockMovement.java
package ru.warehouse.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Атомарная неизменяемая запись об изменении остатка (разд. 3 и 6 ТЗ). */
@Entity
@Table(name = "stock_movements")
@Getter @Setter
public class StockMovement {
    public enum Type { RECEIPT, ISSUE, WRITE_OFF, TRANSFER, ADJUSTMENT, RESERVATION }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private Type movementType;

    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "batch_id") private UUID batchId;
    @Column(name = "from_section_id") private UUID fromSectionId;
    @Column(name = "from_section_code", length = 50) private String fromSectionCode;
    @Column(name = "to_section_id") private UUID toSectionId;
    @Column(name = "to_section_code", length = 50) private String toSectionCode;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_code", nullable = false, length = 20) private String unitCode;
    @Column(name = "document_type", nullable = false, length = 50) private String documentType;
    @Column(name = "document_id", nullable = false) private UUID documentId;
    @Column(name = "performed_by", nullable = false) private UUID performedBy;

    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt = OffsetDateTime.now();

    private String comment;
}