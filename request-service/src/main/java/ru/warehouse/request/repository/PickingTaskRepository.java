// PickingTaskRepository.java
package ru.warehouse.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.request.entity.PickingTask;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PickingTaskRepository extends JpaRepository<PickingTask, UUID>, JpaSpecificationExecutor<PickingTask> {

    interface PickedTotal {
        UUID getRequestItemId();
        BigDecimal getPicked();
    }

    @Query(value = "select nextval('picking_task_number_seq')", nativeQuery = true)
    long nextNumber();

    @Query("select t.request.id from PickingTask t where t.id = :id")
    Optional<UUID> findRequestId(@Param("id") UUID id);

    List<PickingTask> findByRequestIdAndStatusIn(UUID requestId, Collection<PickingTask.Status> statuses);

    /** Фактически собранное по позициям заявки (по завершённым заданиям): основа документа передачи. */
    @Query("select pi.requestItem.id as requestItemId, sum(pi.pickedQuantity) as picked "
            + "from PickingItem pi where pi.task.request.id = :requestId and pi.task.status = :status "
            + "group by pi.requestItem.id")
    List<PickedTotal> pickedTotals(@Param("requestId") UUID requestId, @Param("status") PickingTask.Status status);
}
