// PlacementRuleVersionRepository.java
package ru.warehouse.warehouse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.warehouse.warehouse.entity.PlacementRuleVersion;
import java.util.List;
import java.util.UUID;

public interface PlacementRuleVersionRepository extends JpaRepository<PlacementRuleVersion, UUID> {
    List<PlacementRuleVersion> findByRuleIdOrderByVersionDesc(UUID ruleId);
}