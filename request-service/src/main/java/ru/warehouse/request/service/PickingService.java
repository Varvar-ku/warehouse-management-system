// PickingService.java
package ru.warehouse.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.common.numbering.DocumentNumbers;
import ru.warehouse.common.validation.QuantityRules;
import ru.warehouse.request.client.AuthClient;
import ru.warehouse.request.client.InventoryClient;
import ru.warehouse.request.client.InventoryContract.ReservationDto;
import ru.warehouse.request.dto.PickingDtos.*;
import ru.warehouse.request.entity.*;
import ru.warehouse.request.repository.PickingReassignmentRepository;
import ru.warehouse.request.repository.PickingTaskRepository;
import ru.warehouse.request.repository.RequestRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import ru.warehouse.common.event.OutboxPublisher;
import static ru.warehouse.common.event.OutboxPublisher.details;

/**
 * Комплектация (разд. 12 и 28 ТЗ). Каждая операция блокирует строку заявки: работники одной заявки
 * обрабатываются по очереди, суммы и статусы всегда согласованы.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PickingService {
    private static final EnumSet<PickingTaskWorker.Status> PARTICIPATING = EnumSet.of(
            PickingTaskWorker.Status.ASSIGNED, PickingTaskWorker.Status.ACCEPTED,
            PickingTaskWorker.Status.IN_PROGRESS, PickingTaskWorker.Status.COMPLETED);
    private static final String SERVICE = "request-service";
    private static final String ENTITY = "PICKING_TASK";
    private final OutboxPublisher outbox;
    private final RequestRepository requestRepository;
    private final DocumentNumbers numbers;
    private final PickingTaskRepository taskRepository;
    private final PickingReassignmentRepository reassignmentRepository;
    private final RequestService requestService;
    private final InventoryClient inventoryClient;
    private final AuthClient authClient;
    private final RequestOperations ops;
    /** Аренда операции с внешним вызовом (S2). */
    private static final Duration OPERATION_TTL = Duration.ofSeconds(60);

    private record Locked(PickingTask task, Request request) {}

    // ================= создание задания =================

    private record Prefetched(List<ReservationDto> reservations, Set<UUID> workerIds) {}

    /**
     * S2: резервы из inventory и справочник работников запрашиваются между двумя короткими транзакциями,
     * а не под блокировкой заявки (см. RequestOperations).
     */
    public PickingTaskResponse create(CreatePickingTaskDto dto, Actor actor) {
        boolean withWorkers = dto.workers() != null && !dto.workers().isEmpty();
        return ops.<RequestStatus, Prefetched, PickingTaskResponse>run(dto.requestId(), OPERATION_TTL,
                r -> {
                    requireReserved(r);
                    return r.getStatus();
                },
                status -> new Prefetched(activeReservations(dto.requestId()),
                        withWorkers ? activeWorkerIds() : Set.<UUID>of()),
                (r, status, pre) -> createTask(r, dto, actor, pre));
    }

    private static void requireReserved(Request r) {
        if (r.getStatus() != RequestStatus.RESERVED && r.getStatus() != RequestStatus.RESERVED_PARTIAL) {
            throw ApiException.conflict("INVALID_STATUS", "Задание создаётся для зарезервированной заявки, статус: " + r.getStatus());
        }
    }

    /** Источник истины по резервам: inventory-service. */
    private List<ReservationDto> activeReservations(UUID requestId) {
        return inventoryClient.reservations(requestId).stream()
                .filter(d -> "ACTIVE".equals(d.status()))
                .sorted((a, b) -> {
                    int c = a.sectionCode().compareTo(b.sectionCode());
                    return c != 0 ? c : a.productSku().compareTo(b.productSku());
                }).toList();
    }

    private Set<UUID> activeWorkerIds() {
        return authClient.workers().stream().map(AuthClient.WorkerInfo::id).collect(Collectors.toSet());
    }

    private PickingTaskResponse createTask(Request r, CreatePickingTaskDto dto, Actor actor, Prefetched pre) {
        requireReserved(r);
        List<ReservationDto> reservations = pre.reservations();
        if (reservations.isEmpty()) {
            throw ApiException.conflict("NO_RESERVATIONS", "В inventory-service нет активных резервов по заявке");
        }
        Map<UUID, RequestItem> byId = r.getItems().stream().collect(Collectors.toMap(RequestItem::getId, i -> i));

        PickingTask task = new PickingTask();
        task.setNumber(numbers.next("PT"));
        task.setRequest(r);
        task.setCreatedBy(actor.userId());
        Map<UUID, BigDecimal> reservedByItem = new HashMap<>();
        for (ReservationDto d : reservations) {
            RequestItem ri = byId.get(d.requestItemId());
            if (ri == null) {
                throw ApiException.conflict("RESERVATION_MISMATCH", "Резерв ссылается на позицию, которой нет в заявке: " + d.requestItemId());
            }
            PickingItem row = new PickingItem();
            row.setTask(task);
            row.setRequestItem(ri);
            row.setReservationId(d.id());
            row.setStockId(d.stockId());
            row.setSectionId(d.sectionId());
            row.setSectionCode(d.sectionCode());
            row.setBatchId(d.batchId());
            row.setBatchNumber(d.batchNumber());
            row.setRequestedQuantity(d.quantity());
            task.getItems().add(row);
            reservedByItem.merge(ri.getId(), d.quantity(), BigDecimal::add);
        }
        // Позиции заявки приводим к фактическому резерву (на случай подхваченного после сбоя резерва)
        r.getItems().forEach(i -> i.setReservedQuantity(reservedByItem.getOrDefault(i.getId(), BigDecimal.ZERO)));
        task = taskRepository.save(task);
        requestService.transition(r, RequestStatus.PICKING, actor.userId(), "Задание " + task.getNumber());
        outbox.audit(SERVICE, "picking.task_created", ENTITY, task.getId(), actor.userId(), details(
                "taskNumber", task.getNumber(),
                "requestId", r.getId(),
                "requestNumber", r.getNumber(),
                "rows", task.getItems().size()));
        log.info("AUDIT: создано задание на комплектацию {} по заявке {}, строк {}, кладовщик {}",
                task.getNumber(), r.getNumber(), task.getItems().size(), actor.userId());

        if (dto.workers() != null && !dto.workers().isEmpty()) {
            addWorkersTo(task, dto.workers(), Boolean.TRUE.equals(dto.autoAssignRemaining()), actor, pre.workerIds());
        }
        taskRepository.flush(); // идентификаторы новых работников нужны в ответе
        return PickingMapper.toResponse(task, null);
    }

    // ================= исполнители =================

    /** S2: справочник работников запрашивается вне транзакции (см. RequestOperations). */
    public PickingTaskResponse addWorkers(UUID taskId, AddWorkersDto dto, Actor actor) {
        UUID requestId = taskRepository.findRequestId(taskId)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "Задание не найдено"));
        return ops.<Boolean, Set<UUID>, PickingTaskResponse>run(requestId, OPERATION_TTL,
                r -> {
                    requireOpen(taskRepository.findById(taskId)
                            .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "Задание не найдено")));
                    return Boolean.TRUE;
                },
                ready -> activeWorkerIds(),
                (r, ready, known) -> {
                    PickingTask task = taskRepository.findById(taskId)
                            .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "Задание не найдено"));
                    requireOpen(task);
                    addWorkersTo(task, dto.workers(), Boolean.TRUE.equals(dto.autoAssignRemaining()), actor, known);
                    taskRepository.flush();
                    return PickingMapper.toResponse(task, null);
                });
    }

    @Transactional
    public PickingTaskResponse removeWorker(UUID taskId, UUID workerId, UUID replaceWith, String reason, Actor actor) {
        Locked l = lock(taskId);
        PickingTask task = l.task();
        requireOpen(task);
        PickingTaskWorker w = activeWorker(task, workerId);
        if (w == null) throw ApiException.notFound("WORKER_NOT_FOUND", "Работник не назначен на задание");

        List<PickingItem> pending = task.getItems().stream()
                .filter(i -> workerId.equals(i.getAssignedWorkerId()) && !i.processed()).toList();
        if (!pending.isEmpty()) {
            // Несобранные позиции обязаны перейти к другому исполнителю (п. 28.6 ТЗ)
            if (replaceWith == null) {
                throw ApiException.conflict("UNFINISHED_ITEMS", "У работника " + pending.size()
                        + " несобранных строк: укажите replaceWith (работник, который их заберёт)");
            }
            if (replaceWith.equals(workerId)) throw ApiException.validation("Заменяющий работник совпадает со снимаемым");
            if (activeWorker(task, replaceWith) == null) {
                throw ApiException.validation("Заменяющий работник должен быть назначен на задание (сначала добавьте его)");
            }
            for (PickingItem row : pending) {
                PickingReassignment entry = new PickingReassignment(); // не «log»: не затенять логгер класса
                entry.setTaskId(task.getId());
                entry.setPickingItemId(row.getId());
                entry.setRequestItemId(row.getRequestItem().getId());
                entry.setFromWorkerId(workerId);
                entry.setToWorkerId(replaceWith);
                entry.setQuantity(row.getRequestedQuantity());
                entry.setActorId(actor.userId());
                entry.setReason(reason);
                reassignmentRepository.save(entry);
                row.setAssignedWorkerId(replaceWith);
            }
            w.setStatus(PickingTaskWorker.Status.REASSIGNED);
        } else {
            w.setStatus(PickingTaskWorker.Status.CANCELLED);
        }
        w.setComment(reason);
        boolean wasPrimary = w.getRole() == PickingTaskWorker.Role.PRIMARY;
        if (wasPrimary) w.setRole(PickingTaskWorker.Role.WORKER);
        taskRepository.flush(); // сначала фиксируем снятие, затем назначаем нового основного (уникальный индекс)
        if (wasPrimary) {
            PickingTaskWorker next = replaceWith != null ? activeWorker(task, replaceWith)
                    : task.getWorkers().stream().filter(PickingTaskWorker::isActive).findFirst().orElse(null);
            if (next != null) next.setRole(PickingTaskWorker.Role.PRIMARY);
        }
        log.info("AUDIT: снят работник {} с задания {} (замена: {}), причина: {}, кладовщик {}",
                workerId, task.getNumber(), replaceWith, reason, actor.userId());
        outbox.audit(SERVICE, "picking.worker_removed", ENTITY, task.getId(), actor.userId(), details(
                "taskNumber", task.getNumber(),
                "workerId", workerId,
                "replaceWith", replaceWith,
                "reason", reason,
                "movedRows", pending.size(),
                "movedQuantity", pending.stream().map(PickingItem::getRequestedQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                "outcome", w.getStatus().name()));
        return PickingMapper.toResponse(task, null);
    }

    /** Работник меняет статус своего участия; кладовщик правит роль и комментарий. */
    @Transactional
    public PickingTaskResponse updateWorker(UUID taskId, UUID workerId, UpdateWorkerDto dto, Actor actor) {
        Locked l = lock(taskId);
        PickingTask task = l.task();
        requireOpen(task);
        PickingTaskWorker w = activeWorker(task, workerId);
        if (w == null) throw ApiException.notFound("WORKER_NOT_FOUND", "Работник не назначен на задание");
        boolean self = w.getWorkerId().equals(actor.userId());

        if (dto.status() != null) {
            if (!self) throw ApiException.forbidden("NOT_YOUR_STATUS", "Статус участия меняет сам работник");
            boolean accept = w.getStatus() == PickingTaskWorker.Status.ASSIGNED && dto.status() == PickingTaskWorker.Status.ACCEPTED;
            boolean start = w.getStatus() == PickingTaskWorker.Status.ACCEPTED && dto.status() == PickingTaskWorker.Status.IN_PROGRESS;
            if (!accept && !start) {
                throw ApiException.conflict("INVALID_WORKER_TRANSITION", "Переход " + w.getStatus() + " → " + dto.status()
                        + " недоступен (COMPLETED выставляется автоматически, снятие делает кладовщик)");
            }
            PickingTaskWorker.Status previous = w.getStatus();
            w.setStatus(dto.status());
            outbox.audit(SERVICE, "picking.worker_status_changed", ENTITY, task.getId(), actor.userId(), details(
                    "taskNumber", task.getNumber(),
                    "workerId", workerId,
                    "fromStatus", previous.name(),
                    "toStatus", dto.status().name()));
            if (start) {
                w.setStartedAt(OffsetDateTime.now());
                if (task.getStatus() == PickingTask.Status.NEW) task.setStatus(PickingTask.Status.IN_PROGRESS);
            }
        }
        if (dto.role() != null || dto.comment() != null) {
            if (!actor.storekeeper()) throw ApiException.forbidden("STOREKEEPER_ONLY", "Роль и комментарий меняет кладовщик");
            if (dto.comment() != null) w.setComment(dto.comment().isBlank() ? null : dto.comment().trim());
            if (dto.role() == PickingTaskWorker.Role.PRIMARY) {
                setPrimary(task, w);
                outbox.audit(SERVICE, "picking.worker_role_changed", ENTITY, task.getId(), actor.userId(), details(
                        "taskNumber", task.getNumber(),
                        "workerId", workerId,
                        "newRole", "PRIMARY"));
            }
            else if (dto.role() == PickingTaskWorker.Role.WORKER && w.getRole() == PickingTaskWorker.Role.PRIMARY) {
                throw ApiException.validation("Чтобы сменить основного исполнителя, назначьте основным другого работника");
            }
        }
        return PickingMapper.toResponse(task, viewer(actor));
    }

    // ================= распределение =================

    @Transactional
    public PickingTaskResponse assignItems(UUID taskId, AssignItemsDto dto, Actor actor) {
        Locked l = lock(taskId);
        PickingTask task = l.task();
        requireOpen(task);
        for (AssignmentInput a : dto.assignments()) {
            if (activeWorker(task, a.workerId()) == null) {
                throw ApiException.validation("Работник не назначен на задание: " + a.workerId());
            }
            List<PickingItem> free = task.getItems().stream()
                    .filter(i -> i.getRequestItem().getId().equals(a.requestItemId())
                            && !i.processed() && i.getAssignedWorkerId() == null)
                    .sorted(Comparator.comparing(PickingItem::getSectionCode)) // сортировка стабильная: порядок строк сохраняется
                    .collect(Collectors.toCollection(ArrayList::new));
            if (task.getItems().stream().noneMatch(i -> i.getRequestItem().getId().equals(a.requestItemId()))) {
                throw ApiException.validation("Позиции заявки нет в задании: " + a.requestItemId());
            }
            RequestItem ri = task.getItems().stream().map(PickingItem::getRequestItem)
                    .filter(x -> x.getId().equals(a.requestItemId())).findFirst().orElseThrow();
            QuantityRules.require(ri.getUnitCode(), ri.isUnitAllowFractions(), a.quantity(), "Позиция " + ri.getLineNo());
            task.getItems().addAll(PickingAssigner.assign(free, a.workerId(), a.quantity()));
            outbox.audit(SERVICE, "picking.items_assigned", ENTITY, task.getId(), actor.userId(), details(
                    "taskNumber", task.getNumber(),
                    "requestItemId", a.requestItemId(),
                    "lineNo", ri.getLineNo(),
                    "workerId", a.workerId(),
                    "quantity", a.quantity()));
        }
        taskRepository.flush(); // новые строки после разбиения получают id до формирования ответа
        log.info("AUDIT: распределение задания {}: {} назначений, кладовщик {}",
                task.getNumber(), dto.assignments().size(), actor.userId());
        return PickingMapper.toResponse(task, null);
    }

    // ================= сбор и завершение =================

    @Transactional
    public PickingTaskResponse pick(UUID taskId, UUID itemId, PickDto dto, Actor actor) {
        Locked l = lock(taskId);
        PickingTask task = l.task();
        requireOpen(task);
        PickingItem row = task.getItems().stream().filter(i -> itemId.equals(i.getId())).findFirst()
                .orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "Строка не найдена в задании"));
        if (!actor.userId().equals(row.getAssignedWorkerId())) {
            throw ApiException.forbidden("ITEM_NOT_ASSIGNED", "Строка назначена другому работнику или не назначена");
        }
        if (row.processed()) {
            throw ApiException.conflict("ITEM_ALREADY_PROCESSED", "Строка уже обработана: " + row.getStatus());
        }
        RequestItem ri = row.getRequestItem();
        QuantityRules.require(ri.getUnitCode(), ri.isUnitAllowFractions(), dto.pickedQuantity(), "Собранное количество");
        int cmp = dto.pickedQuantity().compareTo(row.getRequestedQuantity());
        if (cmp > 0) {
            throw ApiException.conflict("PICKED_EXCEEDS_ASSIGNED", "Собрано больше назначенного ("
                    + row.getRequestedQuantity().stripTrailingZeros().toPlainString() + ")");
        }
        if (cmp < 0 && (dto.comment() == null || dto.comment().isBlank())) {
            throw ApiException.validation("При недостаче укажите причину");
        }
        OffsetDateTime now = OffsetDateTime.now();
        row.setStatus(cmp == 0 ? PickingItem.Status.PICKED : PickingItem.Status.SHORTAGE);
        row.setPickedQuantity(dto.pickedQuantity());
        row.setComment(dto.comment() == null || dto.comment().isBlank() ? null : dto.comment().trim());
        row.setPickedAt(now);
        outbox.audit(SERVICE, "picking.item_picked", ENTITY, task.getId(), actor.userId(), details(
                "taskNumber", task.getNumber(),
                "itemId", row.getId(),
                "requestItemId", ri.getId(),
                "lineNo", ri.getLineNo(),
                "sectionCode", row.getSectionCode(),
                "batchNumber", row.getBatchNumber(),
                "assignedQuantity", row.getRequestedQuantity(),
                "pickedQuantity", dto.pickedQuantity(),
                "shortage", cmp < 0,
                "comment", row.getComment()));

        PickingTaskWorker w = activeWorker(task, actor.userId());
        if (w != null && (w.getStatus() == PickingTaskWorker.Status.ASSIGNED
                || w.getStatus() == PickingTaskWorker.Status.ACCEPTED)) {
            w.setStatus(PickingTaskWorker.Status.IN_PROGRESS); // первая собранная строка начинает работу
            if (w.getStartedAt() == null) w.setStartedAt(now);
        }
        if (task.getStatus() == PickingTask.Status.NEW) task.setStatus(PickingTask.Status.IN_PROGRESS);
        boolean mineDone = task.getItems().stream()
                .filter(i -> actor.userId().equals(i.getAssignedWorkerId())).allMatch(PickingItem::processed);
        if (mineDone && w != null) {
            w.setStatus(PickingTaskWorker.Status.COMPLETED);
            w.setCompletedAt(now);
        }
        if (task.getItems().stream().allMatch(PickingItem::processed)) {
            finalizeTask(task, l.request(), actor.userId());
        }
        return PickingMapper.toResponse(task, viewer(actor));
    }

    /** Явное завершение кладовщиком: необработанные строки оформляются как недостача (только с подтверждением). */
    @Transactional
    public PickingTaskResponse complete(UUID taskId, CompleteTaskDto dto, Actor actor) {
        Locked l = lock(taskId);
        PickingTask task = l.task();
        requireOpen(task);
        List<PickingItem> open = task.getItems().stream().filter(i -> !i.processed()).toList();
        if (!open.isEmpty()) {
            if (dto == null || !Boolean.TRUE.equals(dto.confirmShortage())) {
                List<String> details = open.stream().map(i -> "Строка " + i.getSectionCode() + " / "
                        + i.getRequestItem().getProductSku() + ": осталось "
                        + i.getRequestedQuantity().stripTrailingZeros().toPlainString()).toList();
                throw ApiException.conflict("UNFINISHED_ITEMS",
                        "Не все строки обработаны: для завершения подтвердите недостачу (confirmShortage)", details);
            }
            if (dto.comment() == null || dto.comment().isBlank()) {
                throw ApiException.validation("Для оформления недостачи нужен комментарий");
            }
            // S6 (вариант 2): недостачу по собственным строкам оформляет другой кладовщик
            long own = open.stream().filter(i -> actor.userId().equals(i.getAssignedWorkerId())).count();
            if (own > 0) {
                throw ApiException.forbidden("SELF_SHORTAGE_CONFIRMATION", "Недостачу по собственным строкам ("
                        + own + ") оформляет другой кладовщик");
            }
            OffsetDateTime now = OffsetDateTime.now();
            for (PickingItem i : open) {
                i.setStatus(PickingItem.Status.SHORTAGE);
                i.setPickedQuantity(BigDecimal.ZERO);
                i.setComment("Недостача оформлена кладовщиком: " + dto.comment().trim());
                i.setPickedAt(now);
            }
            outbox.audit(SERVICE, "picking.shortage_confirmed", ENTITY, task.getId(), actor.userId(), details(
                    "taskNumber", task.getNumber(),
                    "rows", open.size(),
                    "comment", dto.comment().trim()));
        }
        finalizeTask(task, l.request(), actor.userId());
        return PickingMapper.toResponse(task, null);
    }

    /**
     * S3: исправление ошибочного ввода. Кладовщик переоткрывает обработанную строку (причина обязательна),
     * и работник вводит собранное заново. Работает в открытом задании; в завершённом только пока заявка в статусе
     * PICKED или PICKING_WITH_SHORTAGE (до «готово к выдаче»): задание снова открывается, а заявка возвращается в
     * PICKING (для этого переход должен быть разрешён в RequestStatus).
     */
    @Transactional
    public PickingTaskResponse reopenItem(UUID taskId, UUID itemId, ReopenItemDto dto, Actor actor) {
        Locked l = lock(taskId);
        PickingTask task = l.task();
        Request request = l.request();
        PickingItem row = task.getItems().stream().filter(i -> itemId.equals(i.getId())).findFirst()
                .orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "Строка не найдена в задании"));
        if (!row.processed()) {
            throw ApiException.conflict("ITEM_NOT_PROCESSED", "Строка ещё не обработана, переоткрывать нечего");
        }
        boolean wasClosed = !task.isOpen();
        if (wasClosed && (task.getStatus() != PickingTask.Status.COMPLETED
                || (request.getStatus() != RequestStatus.PICKED
                && request.getStatus() != RequestStatus.PICKING_WITH_SHORTAGE))) {
            throw ApiException.conflict("TASK_CLOSED", "Строку нельзя переоткрыть: задание " + task.getStatus()
                    + ", заявка " + request.getStatus());
        }
        PickingItem.Status previousStatus = row.getStatus();
        BigDecimal previousQuantity = row.getPickedQuantity();
        row.setStatus(PickingItem.Status.PENDING);
        row.setPickedQuantity(null);
        row.setComment(null);
        row.setPickedAt(null);
        if (wasClosed) {
            task.setStatus(PickingTask.Status.IN_PROGRESS);
            task.setCompletedAt(null);
            task.setCompletedBy(null);
            requestService.transition(request, RequestStatus.PICKING, actor.userId(),
                    "Строка переоткрыта: " + dto.reason().trim());
        }
        UUID assignee = row.getAssignedWorkerId();
        if (assignee != null) {
            PickingTaskWorker owner = task.getWorkers().stream()
                    .filter(w -> w.getWorkerId().equals(assignee) && w.isParticipating()).findFirst().orElse(null);
            if (owner == null) {
                row.setAssignedWorkerId(null); // исполнитель уже снят: строку нужно назначить заново
            } else if (owner.getStatus() == PickingTaskWorker.Status.COMPLETED) {
                owner.setStatus(PickingTaskWorker.Status.IN_PROGRESS);
                owner.setCompletedAt(null);
            }
        }
        outbox.audit(SERVICE, "picking.item_reopened", ENTITY, task.getId(), actor.userId(), details(
                "taskNumber", task.getNumber(),
                "itemId", row.getId(),
                "sectionCode", row.getSectionCode(),
                "previousStatus", previousStatus.name(),
                "previousPickedQuantity", previousQuantity,
                "taskWasClosed", wasClosed,
                "reason", dto.reason().trim()));
        log.info("AUDIT: переоткрыта строка {} задания {}, причина: {}, кладовщик {}",
                row.getId(), task.getNumber(), dto.reason().trim(), actor.userId());
        return PickingMapper.toResponse(task, null);
    }

    // ================= чтение =================

    @Transactional(readOnly = true)
    public PickingTaskResponse get(UUID id, Actor actor) {
        return PickingMapper.toResponse(visible(id, actor), viewer(actor));
    }

    @Transactional(readOnly = true)
    public ProgressResponse progress(UUID id, Actor actor) {
        return PickingMapper.progress(visible(id, actor), viewer(actor));
    }

    @Transactional(readOnly = true)
    public List<WorkerResponse> workers(UUID id, Actor actor) {
        return PickingMapper.workers(visible(id, actor), viewer(actor));
    }

    /**
     * Кладовщик видит все задания, работник только те, где он участвует (и только свои строки).
     * S3: ограничение в 200 записей выполняется в БД. Работнику без явного фильтра по статусу показываются открытые
     * задания и завершённые за последние 14 дней (давно завершённые не нужны).
     */
    @Transactional(readOnly = true)
    public List<PickingTaskResponse> list(PickingTask.Status status, UUID requestId, Actor actor) {
        Specification<PickingTask> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (requestId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("request").get("id"), requestId));
        if (!actor.storekeeper()) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                var w = root.join("workers");
                return cb.and(cb.equal(w.get("workerId"), actor.userId()), w.get("status").in(PARTICIPATING));
            });
            if (status == null) {
                OffsetDateTime since = OffsetDateTime.now().minusDays(14);
                spec = spec.and((root, query, cb) -> cb.or(
                        root.get("status").in(PickingTask.Status.NEW, PickingTask.Status.IN_PROGRESS),
                        cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("createdAt"), since)));
            }
        }
        var page = PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt"));
        return taskRepository.findAll(spec, page).stream()
                .map(t -> PickingMapper.toResponse(t, viewer(actor))).toList();
    }

    // ================= внутреннее =================

    private void addWorkersTo(PickingTask task, List<WorkerInput> inputs, boolean autoAssign, Actor actor,
                              Set<UUID> known) {
        Set<UUID> ids = inputs.stream().map(WorkerInput::workerId).collect(Collectors.toSet());
        if (!known.containsAll(ids)) {
            throw ApiException.validation("Назначать можно только активных работников склада (роль WAREHOUSE_WORKER)");
        }
        for (WorkerInput in : inputs) {
            PickingTaskWorker existing = task.getWorkers().stream()
                    .filter(x -> x.getWorkerId().equals(in.workerId())).findFirst().orElse(null);
            PickingTaskWorker w;
            boolean joined = true;
            if (existing != null && existing.isParticipating()) {
                w = existing; // уже назначен: повторное добавление безопасно
                joined = false;
            } else if (existing != null) {
                w = existing; // ранее снятый работник возвращается в задание
                w.setStatus(PickingTaskWorker.Status.ASSIGNED);
                w.setAssignedAt(OffsetDateTime.now());
                w.setStartedAt(null);
                w.setCompletedAt(null);
            } else {
                w = new PickingTaskWorker();
                w.setTask(task);
                w.setWorkerId(in.workerId());
                task.getWorkers().add(w);
            }
            boolean hasPrimary = task.getWorkers().stream()
                    .anyMatch(x -> x != w && x.isActive() && x.getRole() == PickingTaskWorker.Role.PRIMARY);
            if (in.role() == PickingTaskWorker.Role.PRIMARY || (!hasPrimary && in.role() == null)) {
                setPrimary(task, w);
            }
            if (joined) {
                outbox.audit(SERVICE, "picking.worker_added", ENTITY, task.getId(), actor.userId(), details(
                        "taskNumber", task.getNumber(),
                        "workerId", in.workerId(),
                        "role", w.getRole() == null ? null : w.getRole().name(),
                        "returned", existing != null));
            }
        }
        if (autoAssign) {
            List<PickingTaskWorker> active = task.getWorkers().stream().filter(PickingTaskWorker::isActive).toList();
            if (active.size() != 1) {
                throw ApiException.validation("autoAssignRemaining доступно, когда в задании ровно один работник");
            }
            UUID only = active.get(0).getWorkerId();
            List<PickingItem> free = task.getItems().stream()
                    .filter(i -> !i.processed() && i.getAssignedWorkerId() == null).toList();
            free.forEach(i -> i.setAssignedWorkerId(only));
            if (!free.isEmpty()) {
                outbox.audit(SERVICE, "picking.items_auto_assigned", ENTITY, task.getId(), actor.userId(), details(
                        "taskNumber", task.getNumber(),
                        "workerId", only,
                        "rows", free.size()));
            }
        }
        log.info("AUDIT: исполнители задания {}: {}, кладовщик {}", task.getNumber(), ids, actor.userId());
    }

    /** Назначает основного исполнителя: прежний основной становится обычным (сначала сброс, затем назначение). */
    private void setPrimary(PickingTask task, PickingTaskWorker newPrimary) {
        for (PickingTaskWorker w : task.getWorkers()) {
            if (w != newPrimary && w.isActive() && w.getRole() == PickingTaskWorker.Role.PRIMARY) {
                w.setRole(PickingTaskWorker.Role.WORKER);
            }
        }
        if (task.getId() != null) taskRepository.flush();
        newPrimary.setRole(PickingTaskWorker.Role.PRIMARY);
    }

    /** Все строки обработаны: задание завершено, заявка переходит в PICKED либо PICKING_WITH_SHORTAGE. */
    private void finalizeTask(PickingTask task, Request request, UUID actorId) {
        OffsetDateTime now = OffsetDateTime.now();
        task.setStatus(PickingTask.Status.COMPLETED);
        task.setCompletedAt(now);
        task.setCompletedBy(actorId);
        for (PickingTaskWorker w : task.getWorkers()) {
            if (w.isActive()) {
                w.setStatus(PickingTaskWorker.Status.COMPLETED);
                w.setCompletedAt(now);
            }
        }
        boolean shortage = task.getItems().stream().anyMatch(i -> i.getStatus() == PickingItem.Status.SHORTAGE);
        requestService.transition(request, shortage ? RequestStatus.PICKING_WITH_SHORTAGE : RequestStatus.PICKED,
                actorId, "Задание " + task.getNumber() + " завершено");
        outbox.audit(SERVICE, "picking.task_completed", ENTITY, task.getId(), actorId, details(
                "taskNumber", task.getNumber(),
                "requestId", request.getId(),
                "requestNumber", request.getNumber(),
                "outcome", shortage ? "PICKING_WITH_SHORTAGE" : "PICKED"));
        log.info("AUDIT: завершено задание {} по заявке {}: {}", task.getNumber(), request.getNumber(), request.getStatus());
    }

    /** Блокирует заявку, затем читает задание (после блокировки данные свежие). */
    private Locked lock(UUID taskId) {
        UUID requestId = taskRepository.findRequestId(taskId)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "Задание не найдено"));
        Request request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Заявка не найдена"));
        ops.assertFree(requestId); // S2: пока по заявке идёт операция с внешним вызовом, изменения отклоняются
        PickingTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "Задание не найдено"));
        return new Locked(task, request);
    }

    private static void requireOpen(PickingTask task) {
        if (!task.isOpen()) {
            throw ApiException.conflict("TASK_CLOSED", "Задание уже завершено или отклонено: " + task.getStatus());
        }
    }

    private static PickingTaskWorker activeWorker(PickingTask task, UUID workerId) {
        return task.getWorkers().stream()
                .filter(w -> w.getWorkerId().equals(workerId) && w.isActive()).findFirst().orElse(null);
    }

    private PickingTask visible(UUID id, Actor actor) {
        PickingTask t = taskRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "Задание не найдено"));
        if (!actor.storekeeper() && t.getWorkers().stream()
                .noneMatch(w -> w.getWorkerId().equals(actor.userId()) && w.isParticipating())) {
            throw ApiException.notFound("TASK_NOT_FOUND", "Задание не найдено"); // чужое задание не раскрываем
        }
        return t;
    }

    private static UUID viewer(Actor actor) {
        return actor.storekeeper() ? null : actor.userId();
    }
}
