// Issue.java
package ru.warehouse.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Документ передачи (выдачи) по заявке. */
@Entity
@Table(name = "issues")
@Getter @Setter
public class Issue {
    public enum Status { DRAFT, CONFIRMED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @Column(nullable = false, unique = true, length = 50) private String number;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "issued_to", nullable = false) private UUID issuedTo;
    @Column(name = "issued_by") private UUID issuedBy;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "issued_at") private OffsetDateTime issuedAt;
    private String comment;
    @Column(name = "cancel_reason") private String cancelReason;

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IssueItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}