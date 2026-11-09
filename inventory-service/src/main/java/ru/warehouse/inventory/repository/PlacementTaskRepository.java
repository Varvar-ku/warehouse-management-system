// PlacementTaskRepository.java
package ru.warehouse.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.warehouse.inventory.entity.PlacementTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlacementTaskRepository extends JpaRepository<PlacementTask, UUID>, JpaSpecificationExecutor<PlacementTask> {
    boolean existsByReceiptId(UUID receiptId);
    Optional<PlacementTask> findByReceiptId(UUID receiptId);

    @Override
    @EntityGraph(attributePaths = "receipt")
    List<PlacementTask> findAll(Specification<PlacementTask> spec, Sort sort);

    /** M11: постраничная выборка (список заданий не должен быть неограниченным). */
    @Override
    @EntityGraph(attributePaths = "receipt")
    Page<PlacementTask> findAll(Specification<PlacementTask> spec, Pageable pageable);
}
