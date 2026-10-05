package ru.warehouse.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product_attributes")
@Getter @Setter
public class ProductAttribute {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "attribute_code", nullable = false, length = 100)
    private String attributeCode;

    @Column(name = "value_string")
    private String valueString;

    @Column(name = "value_number", precision = 18, scale = 4)
    private BigDecimal valueNumber;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    /** Единица измерения самого значения (например, MM для длины). */
    @Column(name = "value_unit_id")
    private UUID valueUnitId;
}