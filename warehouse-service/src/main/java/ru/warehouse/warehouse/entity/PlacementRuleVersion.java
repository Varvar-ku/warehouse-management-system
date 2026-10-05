// PlacementRuleVersion.java
package ru.warehouse.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Неизменяемый снимок правила на момент версии. */
@Entity
@Table(name = "placement_rule_versions")
@Getter @Setter
public class PlacementRuleVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String snapshot;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt = OffsetDateTime.now();
}