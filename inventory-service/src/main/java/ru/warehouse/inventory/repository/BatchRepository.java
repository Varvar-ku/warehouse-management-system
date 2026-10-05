// BatchRepository.java
package ru.warehouse.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.warehouse.inventory.entity.Batch;
import java.util.UUID;

public interface BatchRepository extends JpaRepository<Batch, UUID> {
    boolean existsByProductIdAndBatchNumber(UUID productId, String batchNumber);
}