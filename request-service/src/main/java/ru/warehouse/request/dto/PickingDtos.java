package ru.warehouse.request.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.warehouse.request.entity.PickingItem;
import ru.warehouse.request.entity.PickingTask;
import ru.warehouse.request.entity.PickingTaskWorker;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** В запросах только ссылочные типы (Boolean): Jackson 3 не принимает отсутствующее примитивное поле. */
public final class PickingDtos {
    private PickingDtos() {}

    // ================= запросы =================

    public record WorkerInput(@NotNull UUID workerId, PickingTaskWorker.Role role) {}   // role = null: WORKER

    /**
     * Создание задания. autoAssignRemaining: если в задании ровно один работник,
     * ему сразу назначаются все позиции (типичный случай «один сборщик»).
     */
    public record CreatePickingTaskDto(@NotNull UUID requestId,
                                       @Size(max = 20) List<@Valid WorkerInput> workers,
                                       Boolean autoAssignRemaining) {}

    public record AddWorkersDto(@NotEmpty @Size(max = 20) List<@Valid WorkerInput> workers,
                                Boolean autoAssignRemaining) {}

    public record AssignmentInput(
            @NotNull UUID requestItemId,
            @NotNull UUID workerId,
            @NotNull @Positive @Digits(integer = 14, fraction = 4) BigDecimal quantity) {}

    public record AssignItemsDto(@NotEmpty @Size(max = 200) List<@Valid AssignmentInput> assignments) {}

    public record PickDto(
            @NotNull @PositiveOrZero @Digits(integer = 14, fraction = 4) BigDecimal pickedQuantity,
            @Size(max = 1000) String comment) {}

    /** Явное завершение с недостачей: только с confirmShortage = true и комментарием. */
    public record CompleteTaskDto(Boolean confirmShortage, @Size(max = 1000) String comment) {}

    public record UpdateWorkerDto(PickingTaskWorker.Status status, PickingTaskWorker.Role role,
                                  @Size(max = 1000) String comment) {}

    public record ReadyDto(@Size(max = 1000) String comment) {}

    /** issuedTo = null: получателем становится автор заявки. */
    public record IssueRequestDto(UUID issuedTo, @Size(max = 1000) String comment) {}

    // ================= ответы =================

    public record PickingItemResponse(
            UUID id, UUID requestItemId, int lineNo, String productSku, String productName, String unitCode,
            UUID sectionId, String sectionCode, String batchNumber, BigDecimal requestedQuantity,
            BigDecimal pickedQuantity, PickingItem.Status status, UUID assignedWorkerId, String comment,
            OffsetDateTime pickedAt) {}

    public record WorkerResponse(UUID id, UUID workerId, PickingTaskWorker.Role role, PickingTaskWorker.Status status,
                                 OffsetDateTime assignedAt, OffsetDateTime startedAt, OffsetDateTime completedAt,
                                 String comment) {}

    public record WorkerProgress(UUID workerId, PickingTaskWorker.Role role, PickingTaskWorker.Status status,
                                 BigDecimal assignedQuantity, BigDecimal pickedQuantity, int itemsTotal,
                                 int itemsProcessed, BigDecimal percent) {}

    /** Общий прогресс задания и (для кладовщика) по каждому работнику; работнику показывается только он сам. */
    public record ProgressResponse(BigDecimal totalQuantity, BigDecimal unassignedQuantity,
                                   BigDecimal processedQuantity, BigDecimal pickedQuantity, int itemsTotal,
                                   int itemsProcessed, BigDecimal percent, List<WorkerProgress> workers) {}

    public record PickingTaskResponse(UUID id, String number, UUID requestId, String requestNumber,
                                      PickingTask.Status status, OffsetDateTime createdAt, OffsetDateTime completedAt,
                                      List<WorkerResponse> workers, List<PickingItemResponse> items,
                                      ProgressResponse progress) {}
}
