package ru.warehouse.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "attribute_definitions")
@Getter @Setter
public class AttributeDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private AttributeDataType dataType;

    /** Какого типа должна быть единица значения (null: без ограничений). Для длины и массы. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UnitDimension dimension;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Допустимые значения; используется только для типа REFERENCE. */
    @ElementCollection
    @CollectionTable(name = "attribute_options", joinColumns = @JoinColumn(name = "attribute_id"))
    @Column(name = "value", nullable = false, length = 150)
    private Set<String> options = new HashSet<>();
}