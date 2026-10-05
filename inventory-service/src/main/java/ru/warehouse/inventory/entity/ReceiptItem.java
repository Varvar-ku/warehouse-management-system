// ReceiptItem.java
package ru.warehouse.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "receipt_items")
@Getter @Setter
public class ReceiptItem {
    public enum PlacementStatus { NEW, PENDING, PLACED, DISCREPANCY }
    public enum TargetDecision { RECOMMENDED, ALTERNATIVE, UNASSIGNED_ZONE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    // Снимок номенклатуры на момент создания документа
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "product_sku", nullable = false, length = 100) private String productSku;
    @Column(name = "product_name", nullable = false) private String productName;
    @Column(name = "product_type_code", nullable = false, length = 50) private String productTypeCode;
    @Column(name = "unit_code", nullable = false, length = 20) private String unitCode;
    @Column(name = "unit_allow_fractions", nullable = false)
    private boolean unitAllowFractions = true;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "expiry_at")
    private OffsetDateTime expiryAt;

    /** JSON уточнений характеристик (CharacteristicsDto). */
    @Column(name = "characteristics_json", nullable = false)
    private String characteristicsJson = "{}";

    /** JSON снимка на момент приёмки; переходит в attributes_snapshot_json партии. */
    @Column(name = "snapshot_json")
    private String snapshotJson;

    private String comment;

    // Рекомендация подбора и её основание
    @Column(name = "recommended_section_id") private UUID recommendedSectionId;
    @Column(name = "recommended_section_code", length = 50) private String recommendedSectionCode;
    @Column(name = "recommendation_strategy", length = 30) private String recommendationStrategy;
    @Column(name = "rule_id") private UUID ruleId;
    @Column(name = "rule_version") private Integer ruleVersion;
    @Column(name = "rule_name") private String ruleName;
    @Column(name = "recommendation_calculated_at") private OffsetDateTime recommendationCalculatedAt;
    @Column(name = "recommendation_notes") private String recommendationNotes;

    // Решение кладовщика
    @Column(name = "target_section_id") private UUID targetSectionId;
    @Column(name = "target_section_code", length = 50) private String targetSectionCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "target_decision", length = 30)
    private TargetDecision targetDecision;

    // Размещение
    @Enumerated(EnumType.STRING)
    @Column(name = "placement_status", nullable = false, length = 20)
    private PlacementStatus placementStatus = PlacementStatus.NEW;
    @Column(name = "placed_section_id") private UUID placedSectionId;
    @Column(name = "placed_section_code", length = 50) private String placedSectionCode;
    @Column(name = "placed_quantity", precision = 18, scale = 4) private BigDecimal placedQuantity;
    @Column(name = "placed_by") private UUID placedBy;
    @Column(name = "placed_at") private OffsetDateTime placedAt;
    @Column(name = "discrepancy_comment") private String discrepancyComment;
    @Column(name = "batch_id") private UUID batchId;

    /** Номер партии: введённый кладовщиком или «<номер документа>-<строка>». */
    public String effectiveBatchNumber() {
        return batchNumber != null ? batchNumber : receipt.getNumber() + "-" + lineNo;
    }
}