// Section.java
package ru.warehouse.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "sections")
@Getter @Setter
public class Section {
    /** Код служебной зоны «Нераспределено»; обычную секцию с таким кодом создать нельзя. */
    public static final String UNASSIGNED_CODE = "UNASSIGNED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "section_type", length = 50)
    private String sectionType;

    // Диапазоны размеров в миллиметрах; null = граница не задана
    @Column(name = "length_min", precision = 18, scale = 4) private BigDecimal lengthMin;
    @Column(name = "length_max", precision = 18, scale = 4) private BigDecimal lengthMax;
    @Column(name = "width_min", precision = 18, scale = 4) private BigDecimal widthMin;
    @Column(name = "width_max", precision = 18, scale = 4) private BigDecimal widthMax;
    @Column(name = "height_min", precision = 18, scale = 4) private BigDecimal heightMin;
    @Column(name = "height_max", precision = 18, scale = 4) private BigDecimal heightMax;

    @Column(name = "max_capacity", precision = 18, scale = 4)
    private BigDecimal maxCapacity;

    @Column(name = "capacity_unit_code", length = 20)
    private String capacityUnitCode;

    @Column(length = 100)
    private String zone;

    @Column(length = 100)
    private String rack;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SectionStatus status = SectionStatus.ACTIVE;

    @Column(nullable = false)
    private int priority;

    @Column(name = "is_unassigned_zone", nullable = false)
    private boolean unassignedZone;

    /** Пусто = секция принимает любые типы деталей. Коды хранятся в верхнем регистре. */
    @ElementCollection
    @CollectionTable(name = "section_allowed_product_types", joinColumns = @JoinColumn(name = "section_id"))
    @Column(name = "product_type_code", nullable = false, length = 50)
    private Set<String> allowedProductTypes = new HashSet<>();

    /** Пусто = любые материалы. */
    @ElementCollection
    @CollectionTable(name = "section_allowed_materials", joinColumns = @JoinColumn(name = "section_id"))
    @Column(name = "material_code", nullable = false, length = 150)
    private Set<String> allowedMaterials = new HashSet<>();

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