// AttributeDefinitionRepository.java
package ru.warehouse.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.warehouse.catalog.entity.AttributeDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, UUID> {
    boolean existsByCode(String code);
    Optional<AttributeDefinition> findByCode(String code);
    List<AttributeDefinition> findAllByOrderByCodeAsc();
}