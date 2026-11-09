package ru.warehouse.report.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PickingDtos {

    private PickingDtos() {
    }

    private static List<UUID> ids(UUID... ids) {
        return Arrays.stream(ids).filter(Objects::nonNull).toList();
    }

    /**
     * Строка журнала комплектации: кто, когда, что и в каком количестве.
     * quantity: назначенное (items_assigned, item_picked) или переданное при снятии (worker_removed);
     * pickedQuantity: фактически собранное (item_picked); note: причина или комментарий.
     */
    public record PickingEventRow(
            OffsetDateTime occurredAt, String action, String taskNumber,
            UUID actorId, String actorName,
            UUID workerId, String workerName,
            UUID replaceWith, String replaceWithName,
            String requestNumber, Integer lineNo, String sectionCode, String batchNumber,
            BigDecimal quantity, BigDecimal pickedQuantity, Boolean shortage, String note)
            implements Named<PickingEventRow> {

        @Override
        public List<UUID> peopleIds() {
            return ids(actorId, workerId, replaceWith);
        }

        @Override
        public PickingEventRow withNames(Map<UUID, String> n) {
            return new PickingEventRow(occurredAt, action, taskNumber, actorId, n.get(actorId), workerId,
                    n.get(workerId), replaceWith, n.get(replaceWith), requestNumber, lineNo, sectionCode,
                    batchNumber, quantity, pickedQuantity, shortage, note);
        }
    }

    /** Сводка по исполнителю за период. */
    public record WorkerPerformanceRow(
            UUID workerId, String workerName,
            long tasks, long rowsProcessed,
            BigDecimal assignedQuantity, BigDecimal pickedQuantity, long shortageRows,
            OffsetDateTime firstPickAt, OffsetDateTime lastPickAt,
            long removals, BigDecimal movedQuantity) implements Named<WorkerPerformanceRow> {

        @Override
        public List<UUID> peopleIds() {
            return ids(workerId);
        }

        @Override
        public WorkerPerformanceRow withNames(Map<UUID, String> n) {
            return new WorkerPerformanceRow(workerId, n.get(workerId), tasks, rowsProcessed, assignedQuantity,
                    pickedQuantity, shortageRows, firstPickAt, lastPickAt, removals, movedQuantity);
        }
    }
}
