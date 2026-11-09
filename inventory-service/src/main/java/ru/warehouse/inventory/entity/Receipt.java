// Receipt.java
package ru.warehouse.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "receipts")
@Getter @Setter
public class Receipt {
    public enum Status { DRAFT, RECEIVING, PLACEMENT, DONE, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @Column(nullable = false, unique = true, length = 50)
    private String number;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "external_document_number", length = 100)
    private String externalDocumentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.DRAFT;

    /**
     * Дата приёмки (M8): назначает сервер при создании и подтверждении, пользователь её не вводит. Она же становится датой
     * поступления партии (по ней строится порядок FIFO), поэтому даты приёмки и поступления всегда совпадают.
     */
    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    /** Дата накладной поставщика: вводит пользователь, справочное поле, ни на что не влияет. */
    @Column(name = "document_date")
    private LocalDate documentDate;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<ReceiptItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    public void touch() {
        updatedAt = OffsetDateTime.now();
    }
}