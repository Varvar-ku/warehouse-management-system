package ru.warehouse.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "units")
@Getter @Setter
public class Unit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    /** Тип величины (длина, масса, штуки): по нему проверяется единица значения числового атрибута. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitDimension dimension = UnitDimension.OTHER;

    /** false = количество в этой единице должно быть целым (штуки). */
    @Column(name = "allow_fractions", nullable = false)
    private boolean allowFractions = true;
}