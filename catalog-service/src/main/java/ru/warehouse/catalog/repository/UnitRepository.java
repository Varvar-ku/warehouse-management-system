// UnitRepository.java
package ru.warehouse.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.warehouse.catalog.entity.Unit;
import java.util.List;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {
    boolean existsByCode(String code);
    List<Unit> findAllByOrderByCodeAsc();
}