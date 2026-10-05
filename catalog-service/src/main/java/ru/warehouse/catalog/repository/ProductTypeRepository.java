// ProductTypeRepository.java
package ru.warehouse.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.warehouse.catalog.entity.ProductType;
import java.util.List;
import java.util.UUID;

public interface ProductTypeRepository extends JpaRepository<ProductType, UUID> {
    boolean existsByCode(String code);
    List<ProductType> findAllByOrderByCodeAsc();
    List<ProductType> findByActiveTrueOrderByCodeAsc();
}