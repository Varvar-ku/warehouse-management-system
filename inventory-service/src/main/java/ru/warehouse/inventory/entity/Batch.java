// Batch.java
package ru.warehouse.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "batches")
@Getter @Setter
public class Batch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "product_sku", nullable = false, length = 100) private String productSku;
    @Column(name = "product_name", nullable = false) private String productName;
    @Column(name = "unit_code", nullable = false, length = 20) private String unitCode;
    @Column(name = "receipt_id", nullable = false) private UUID receiptId;
    @Column(name = "batch_number", nullable = false, length = 100) private String batchNumber;
    @Column(name = "received_at", nullable = false) private OffsetDateTime receivedAt;
    @Column(name = "expiry_at") private OffsetDateTime expiryAt;

    @Column(name = "attributes_snapshot_json", nullable = false)
    private String attributesSnapshotJson = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "unit_allow_fractions", nullable = false)
    private boolean unitAllowFractions = true;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}