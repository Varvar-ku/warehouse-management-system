// ProductRepository.java
package ru.warehouse.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.warehouse.catalog.entity.Product;
import java.util.UUID;

// JpaSpecificationExecutor позволяет собирать поисковый запрос из необязательных фильтров
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    boolean existsBySku(String sku);

    /** Подгружаем тип и единицу одним запросом со списком, чтобы не было N+1. */
    @Override
    @EntityGraph(attributePaths = {"productType", "unit"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);
}