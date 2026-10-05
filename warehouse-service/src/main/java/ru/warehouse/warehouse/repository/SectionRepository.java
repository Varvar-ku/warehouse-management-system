// SectionRepository.java
package ru.warehouse.warehouse.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.warehouse.warehouse.entity.Section;
import ru.warehouse.warehouse.entity.SectionStatus;
import java.util.List;
import java.util.UUID;

public interface SectionRepository extends JpaRepository<Section, UUID>, JpaSpecificationExecutor<Section> {
    boolean existsByWarehouseIdAndCode(UUID warehouseId, String code);

    /** Все секции склада, кроме архивных (нужны движку подбора). */
    List<Section> findByWarehouseIdAndStatusNot(UUID warehouseId, SectionStatus status);

    /** Склад подгружаем вместе со списком, чтобы не было N+1. */
    @Override
    @EntityGraph(attributePaths = "warehouse")
    Page<Section> findAll(Specification<Section> spec, Pageable pageable);
}