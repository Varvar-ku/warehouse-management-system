// PickingMapper.java
package ru.warehouse.request.service;

import ru.warehouse.request.dto.PickingDtos.*;
import ru.warehouse.request.entity.PickingItem;
import ru.warehouse.request.entity.PickingTask;
import ru.warehouse.request.entity.PickingTaskWorker;
import ru.warehouse.request.entity.RequestItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Преобразование в ответы. viewerWorkerId = null: полный вид кладовщика; иначе вид работника (только его строки). */
final class PickingMapper {
    private PickingMapper() {}

    static PickingTaskResponse toResponse(PickingTask t, UUID viewerWorkerId) {
        List<PickingItemResponse> items = t.getItems().stream()
                .filter(i -> viewerWorkerId == null || viewerWorkerId.equals(i.getAssignedWorkerId()))
                .sorted((a, b) -> {
                    int c = a.getSectionCode().compareTo(b.getSectionCode());
                    return c != 0 ? c : Integer.compare(a.getRequestItem().getLineNo(), b.getRequestItem().getLineNo());
                })
                .map(PickingMapper::toItem).toList();
        return new PickingTaskResponse(t.getId(), t.getNumber(), t.getRequest().getId(), t.getRequest().getNumber(),
                t.getStatus(), t.getCreatedAt(), t.getCompletedAt(), workers(t, viewerWorkerId), items,
                progress(t, viewerWorkerId));
    }

    static List<WorkerResponse> workers(PickingTask t, UUID viewerWorkerId) {
        return t.getWorkers().stream()
                .filter(w -> viewerWorkerId == null || w.getWorkerId().equals(viewerWorkerId))
                .map(w -> new WorkerResponse(w.getId(), w.getWorkerId(), w.getRole(), w.getStatus(), w.getAssignedAt(),
                        w.getStartedAt(), w.getCompletedAt(), w.getComment())).toList();
    }

    static PickingItemResponse toItem(PickingItem i) {
        RequestItem ri = i.getRequestItem();
        return new PickingItemResponse(i.getId(), ri.getId(), ri.getLineNo(), ri.getProductSku(), ri.getProductName(),
                ri.getUnitCode(), i.getSectionId(), i.getSectionCode(), i.getBatchNumber(), i.getRequestedQuantity(),
                i.getPickedQuantity(), i.getStatus(), i.getAssignedWorkerId(), i.getComment(), i.getPickedAt());
    }

    /** Прогресс по количеству: обработанным считается собранное и оформленная недостача. */
    static ProgressResponse progress(PickingTask t, UUID viewerWorkerId) {
        BigDecimal total = BigDecimal.ZERO, unassigned = BigDecimal.ZERO, processed = BigDecimal.ZERO,
                picked = BigDecimal.ZERO;
        int processedRows = 0;
        for (PickingItem i : t.getItems()) {
            total = total.add(i.getRequestedQuantity());
            if (i.getAssignedWorkerId() == null && !i.processed()) unassigned = unassigned.add(i.getRequestedQuantity());
            if (i.processed()) {
                processed = processed.add(i.getRequestedQuantity());
                processedRows++;
            }
            if (i.getPickedQuantity() != null) picked = picked.add(i.getPickedQuantity());
        }
        List<WorkerProgress> workers = new ArrayList<>();
        for (PickingTaskWorker w : t.getWorkers()) {
            if (viewerWorkerId != null && !w.getWorkerId().equals(viewerWorkerId)) continue;
            BigDecimal assigned = BigDecimal.ZERO, done = BigDecimal.ZERO, doneRequested = BigDecimal.ZERO;
            int rows = 0, doneRows = 0;
            for (PickingItem i : t.getItems()) {
                if (!w.getWorkerId().equals(i.getAssignedWorkerId())) continue;
                rows++;
                assigned = assigned.add(i.getRequestedQuantity());
                if (i.processed()) {
                    doneRows++;
                    doneRequested = doneRequested.add(i.getRequestedQuantity());
                }
                if (i.getPickedQuantity() != null) done = done.add(i.getPickedQuantity());
            }
            workers.add(new WorkerProgress(w.getWorkerId(), w.getRole(), w.getStatus(), assigned, done, rows, doneRows,
                    percent(doneRequested, assigned)));
        }
        return new ProgressResponse(total, unassigned, processed, picked, t.getItems().size(), processedRows,
                percent(processed, total), workers);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) return BigDecimal.ZERO.setScale(1);
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }
}
