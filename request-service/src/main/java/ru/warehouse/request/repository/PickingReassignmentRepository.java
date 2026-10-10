// PickingReassignmentRepository.java
package ru.warehouse.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.warehouse.request.entity.PickingReassignment;
import java.util.UUID;

public interface PickingReassignmentRepository extends JpaRepository<PickingReassignment, UUID> {
}