// WarehouseRepository.java
package ru.warehouse.warehouse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.warehouse.warehouse.entity.Warehouse;
import java.util.List;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    boolean existsByCode(String code);
    List<Warehouse> findAllByOrderByCodeAsc();
}