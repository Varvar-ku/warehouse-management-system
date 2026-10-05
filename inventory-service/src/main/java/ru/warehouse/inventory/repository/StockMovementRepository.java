// StockMovementRepository.java
package ru.warehouse.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.warehouse.inventory.entity.StockMovement;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID>, JpaSpecificationExecutor<StockMovement> {
}