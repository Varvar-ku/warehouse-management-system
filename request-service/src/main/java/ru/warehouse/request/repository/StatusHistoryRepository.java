// StatusHistoryRepository.java
package ru.warehouse.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.warehouse.request.entity.StatusHistory;
import java.util.List;
import java.util.UUID;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {
    List<StatusHistory> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}