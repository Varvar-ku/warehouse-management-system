// WriteOff.java
package ru.warehouse.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "write_offs")
@Getter @Setter
public class WriteOff {
    public enum Status { DRAFT, CONFIRMED, CANCELLED }
    /** Справочник причин списания (в ТЗ: VARCHAR(50); перечень принят мной, расширяемый). */
    public enum Reason { DAMAGED, EXPIRED, LOST, SCRAP, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @Column(nullable = false, unique = true, length = 50) private String number;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 50)
    private Reason reason;

    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "approval_required", nullable = false) private boolean approvalRequired;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private OffsetDateTime approvedAt;
    @Column(name = "confirmed_by") private UUID confirmedBy;
    @Column(name = "confirmed_at") private OffsetDateTime confirmedAt;
    @Column(name = "cancelled_by") private UUID cancelledBy;
    @Column(name = "cancel_reason") private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "writeOff", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WriteOffItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}