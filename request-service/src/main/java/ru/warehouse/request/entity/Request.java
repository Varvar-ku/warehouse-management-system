// Request.java
package ru.warehouse.request.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "requests")
@Getter @Setter
public class Request {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @Column(nullable = false, unique = true, length = 50) private String number;
    @Column(name = "requester_id", nullable = false, updatable = false) private UUID requesterId;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RequestStatus status = RequestStatus.DRAFT;

    @Column(nullable = false) private int priority;
    private String comment;
    @Column(name = "status_reason") private String statusReason;

    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "submitted_at") private OffsetDateTime submittedAt;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_at") private OffsetDateTime reviewedAt;
    @Column(name = "closed_at") private OffsetDateTime closedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<RequestItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @Column(name = "issue_id") private UUID issueId;
    @Column(name = "issue_number", length = 50) private String issueNumber;

    public void touch() {
        updatedAt = OffsetDateTime.now();
    }
}