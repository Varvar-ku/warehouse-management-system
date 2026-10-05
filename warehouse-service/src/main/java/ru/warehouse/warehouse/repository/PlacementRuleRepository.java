// PlacementRuleRepository.java
package ru.warehouse.warehouse.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.warehouse.entity.PlacementRule;
import java.util.List;
import java.util.UUID;

public interface PlacementRuleRepository extends JpaRepository<PlacementRule, UUID>, JpaSpecificationExecutor<PlacementRule> {

    /** Склад правила определяется целевой секцией. */
    @Query("select r from PlacementRule r join fetch r.targetSection s "
            + "where s.warehouse.id = :warehouseId and r.active = true")
    List<PlacementRule> findActiveByWarehouse(@Param("warehouseId") UUID warehouseId);

    boolean existsByTargetSectionIdAndActiveTrue(UUID sectionId);

    @Override
    @EntityGraph(attributePaths = "targetSection")
    Page<PlacementRule> findAll(Specification<PlacementRule> spec, Pageable pageable);
}