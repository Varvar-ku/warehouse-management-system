// PlacementRule.java
package ru.warehouse.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "placement_rules")
@Getter @Setter
public class PlacementRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Технический номер для оптимистической блокировки; не путать с ruleVersion. */
    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    /** Бизнес-версия правила: именно её сохраняет поступление как «основание выбора». */
    @Column(name = "rule_version", nullable = false)
    private int ruleVersion = 1;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int priority;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_fallback", nullable = false)
    private boolean fallback;

    /** JSON условий (RuleConditions) в виде текста. */
    @Column(name = "conditions_json", nullable = false)
    private String conditionsJson = "{}";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_section_id", nullable = false)
    private Section targetSection;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    public void touch() {
        updatedAt = OffsetDateTime.now();
    }
}