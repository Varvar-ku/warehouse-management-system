// RequestService.java
package ru.warehouse.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.common.numbering.DocumentNumbers;
import ru.warehouse.common.validation.QuantityRules;
import ru.warehouse.request.client.CatalogClient;
import ru.warehouse.request.client.CatalogClient.Product;
import ru.warehouse.request.client.InventoryClient;
import ru.warehouse.request.client.InventoryContract.*;
import ru.warehouse.request.client.WarehouseClient;
import ru.warehouse.request.client.WarehouseClient.WarehouseInfo;
import ru.warehouse.request.dto.RequestDtos.*;
import ru.warehouse.request.entity.Request;
import ru.warehouse.request.entity.RequestItem;
import ru.warehouse.request.entity.RequestStatus;
import ru.warehouse.request.entity.StatusHistory;
import ru.warehouse.request.repository.RequestRepository;
import ru.warehouse.request.repository.StatusHistoryRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import ru.warehouse.request.entity.PickingItem;
import ru.warehouse.request.entity.PickingTask;
import ru.warehouse.request.repository.PickingTaskRepository;
import ru.warehouse.common.event.OutboxPublisher;
import static ru.warehouse.common.event.OutboxPublisher.details;
import java.util.Objects;
import java.util.function.Function;

/** Жизненный цикл заявки до резервирования (разд. 5.4, 8 и 11 ТЗ). Комплектация и выдача: часть 5б. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestService {
    private static final Set<RequestStatus> RESERVED_STATES = EnumSet.of(RequestStatus.RESERVED,
            RequestStatus.RESERVED_PARTIAL, RequestStatus.PICKING, RequestStatus.PICKING_WITH_SHORTAGE,
            RequestStatus.READY_FOR_ISSUE);

    private final RequestRepository requestRepository;
    private final DocumentNumbers numbers;
    private final StatusHistoryRepository historyRepository;
    private final CatalogClient catalogClient;
    private final WarehouseClient warehouseClient;
    private final InventoryClient inventoryClient;
    private final PickingTaskRepository pickingTaskRepository;
    private static final String SERVICE = "request-service";
    private final OutboxPublisher outbox;
    private final RequestOperations ops;
    private final TransactionTemplate tx;
    /** Аренда операции с внешним вызовом (два вызова по 2+10 с с запасом). */
    private static final Duration OPERATION_TTL = Duration.ofSeconds(60);

    private static String total(Request r, Function<RequestItem, BigDecimal> field) {
        return r.getItems().stream().map(field).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).toPlainString();
    }

    // ================= пользователь: черновик и отправка =================

    public RequestResponse create(CreateRequestDto dto, Actor actor) {
        // S2: обращения к warehouse-service и catalog-service выполняются до открытия транзакции
        WarehouseInfo warehouse = warehouseClient.warehouse(dto.warehouseId());
        if (!warehouse.active()) {
            throw ApiException.conflict("WAREHOUSE_INACTIVE", "Склад отключён");
        }
        Map<UUID, Product> products = loadProducts(
                dto.items().stream().map(ItemInput::productId).collect(Collectors.toSet()));
        return tx.execute(status -> {
            Request r = new Request();
            r.setNumber(numbers.next("RQ"));
            r.setRequesterId(actor.userId());
            r.setWarehouseId(warehouse.id());
            r.setPriority(dto.priority() == null ? 0 : dto.priority());
            r.setComment(blankToNull(dto.comment()));
            fillItems(r, dto.items(), products);
            r = requestRepository.save(r);
            log.info("AUDIT: создана заявка {} ({}), пользователь {}", r.getNumber(), r.getId(), actor.userId());
            return RequestMapper.toResponse(r);
        });
    }

    public RequestResponse update(UUID id, UpdateRequestDto dto, Actor actor) {
        if (dto.items() != null && dto.items().isEmpty()) {
            throw ApiException.validation("Список позиций не может быть пустым");
        }
        Map<UUID, Product> products = dto.items() == null ? null : loadProducts(
                dto.items().stream().map(ItemInput::productId).collect(Collectors.toSet()));
        return tx.execute(status -> {
            Request r = lockOwn(id, actor);
            requireStatus(r, RequestStatus.DRAFT);
            if (dto.priority() != null) r.setPriority(dto.priority());
            if (dto.comment() != null) r.setComment(blankToNull(dto.comment()));
            if (dto.items() != null) fillItems(r, dto.items(), products);
            r.touch();
            return RequestMapper.toResponse(r);
        });
    }

    /** Удаление черновика (разд. 8 ТЗ). Истории у черновика ещё нет, поэтому физическое удаление допустимо. */
    @Transactional
    public void deleteDraft(UUID id, Actor actor) {
        Request r = lockOwn(id, actor);
        requireStatus(r, RequestStatus.DRAFT);
        requestRepository.delete(r);
        log.info("AUDIT: удалён черновик заявки {}, пользователь {}", r.getNumber(), actor.userId());
    }

    public RequestResponse submit(UUID id, Actor actor) {
        // S2: номенклатура должна существовать и не быть в архиве (разд. 11 ТЗ): проверяем вне транзакции
        Set<UUID> ids = tx.execute(status -> {
            Request r = requestRepository.findById(id)
                    .filter(x -> x.getRequesterId().equals(actor.userId()))
                    .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Заявка не найдена"));
            requireStatus(r, RequestStatus.DRAFT);
            if (r.getItems().isEmpty()) throw ApiException.validation("В заявке нет позиций");
            return productIds(r);
        });
        loadProducts(ids);
        return tx.execute(status -> {
            Request r = lockOwn(id, actor);
            requireStatus(r, RequestStatus.DRAFT);
            if (r.getItems().isEmpty()) throw ApiException.validation("В заявке нет позиций");
            if (!productIds(r).equals(ids)) {
                throw ApiException.conflict("CONCURRENT_MODIFICATION", "Состав заявки изменился во время проверки: повторите отправку");
            }
            transition(r, RequestStatus.SUBMITTED, actor.userId(), null);
            r.setSubmittedAt(OffsetDateTime.now());
            log.info("AUDIT: отправлена заявка {} ({}), пользователь {}", r.getNumber(), r.getId(), actor.userId());
            return RequestMapper.toResponse(r);
        });
    }

    // ================= кладовщик: проверка наличия, решение, резерв =================

    private record ReviewPlan(RequestStatus previous, UUID warehouseId, List<AvailabilityLine> lines) {}

    /**
     * Проверка наличия (B6: промежуточного UNDER_REVIEW больше нет): расчёт доступного количества по каждой
     * позиции и сразу итог APPROVED (всё доступно) или PARTIALLY_AVAILABLE. Допустима и как повторная проверка:
     * если итоговый статус не изменился, обновляются количества, история статусов не пополняется.
     * S2: запрос к inventory идёт между двумя короткими транзакциями (см. RequestOperations).
     */
    public RequestResponse review(UUID id, Actor actor) {
        return ops.<ReviewPlan, List<AvailabilityItem>, RequestResponse>run(id, OPERATION_TTL,
                r -> {
                    if (!EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.APPROVED, RequestStatus.PARTIALLY_AVAILABLE)
                            .contains(r.getStatus())) {
                        throw ApiException.conflict("INVALID_STATUS", "Проверка наличия недоступна в статусе " + r.getStatus());
                    }
                    // Спрос по номенклатуре суммируем: inventory принимает каждую номенклатуру один раз
                    Map<UUID, BigDecimal> demand = new LinkedHashMap<>();
                    for (RequestItem i : r.getItems()) demand.merge(i.getProductId(), i.getRequestedQuantity(), BigDecimal::add);
                    List<AvailabilityLine> lines = demand.entrySet().stream()
                            .map(e -> new AvailabilityLine(e.getKey(), e.getValue())).toList();
                    return new ReviewPlan(r.getStatus(), r.getWarehouseId(), lines);
                },
                plan -> inventoryClient.availability(plan.warehouseId(), plan.lines()),
                (r, plan, availability) -> applyReview(r, plan.previous(), availability, actor));
    }

    private RequestResponse applyReview(Request r, RequestStatus previous, List<AvailabilityItem> availability, Actor actor) {
        requireStatus(r, previous);
        r.setReviewedBy(actor.userId());
        r.setReviewedAt(OffsetDateTime.now());
        Map<UUID, BigDecimal> available = new HashMap<>();
        for (AvailabilityItem a : availability) {
            available.put(a.productId(), a.available());
        }
        List<BigDecimal> approved = AvailabilityAllocator.allocate(
                r.getItems().stream().map(RequestItem::getProductId).toList(),
                r.getItems().stream().map(RequestItem::getRequestedQuantity).toList(), available);
        boolean everythingAvailable = true;
        for (int i = 0; i < r.getItems().size(); i++) {
            RequestItem item = r.getItems().get(i);
            item.setApprovedQuantity(approved.get(i));
            if (approved.get(i).compareTo(item.getRequestedQuantity()) < 0) everythingAvailable = false;
        }
        RequestStatus target = everythingAvailable ? RequestStatus.APPROVED : RequestStatus.PARTIALLY_AVAILABLE;
        String note = everythingAvailable ? "Все позиции доступны" : "Доступна только часть";
        if (target == previous) {
            // повторная проверка без смены статуса: количества обновлены, фиксируем в аудите
            r.touch();
            outbox.audit(SERVICE, "request.rechecked", "REQUEST", r.getId(), actor.userId(), details(
                    "requestNumber", r.getNumber(),
                    "status", target.name(),
                    "approvedQuantity", total(r, RequestItem::getApprovedQuantity),
                    "note", note));
        } else {
            transition(r, target, actor.userId(), note);
        }
        log.info("AUDIT: проверка наличия по заявке {}: {}, кладовщик {}", r.getNumber(), r.getStatus(), actor.userId());
        return RequestMapper.toResponse(r);
    }

    /**
     * Отклонение кладовщиком (B6): из SUBMITTED, APPROVED и PARTIALLY_AVAILABLE, пока резерва нет.
     * После резерва заявку можно только отменить. B5: осиротевший резерв снимается перед отклонением.
     */
    public RequestResponse reject(UUID id, String reason, Actor actor) {
        return ops.<RequestStatus, ReleaseResponse, RequestResponse>run(id, OPERATION_TTL,
                r -> {
                    if (!EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.APPROVED, RequestStatus.PARTIALLY_AVAILABLE)
                            .contains(r.getStatus())) {
                        throw ApiException.conflict("REJECT_NOT_ALLOWED", "Отклонение недоступно в статусе " + r.getStatus());
                    }
                    return r.getStatus();
                },
                status -> releaseOrphanedReservation(id, status),
                (r, status, released) -> {
                    requireStatus(r, status);
                    recordOrphanRelease(r, released, actor);
                    transition(r, RequestStatus.REJECTED, actor.userId(), reason.trim());
                    r.setStatusReason(reason.trim());
                    log.info("AUDIT: отклонена заявка {}, причина: {}, кладовщик {}", r.getNumber(), reason, actor.userId());
                    return RequestMapper.toResponse(r);
                });
    }

    private record ReservePlan(boolean partial, String number, UUID warehouseId, List<ReserveLine> lines,
                               Map<UUID, BigDecimal> approvals) {}

    /**
     * Резервирование (разд. 11 ТЗ). Резерв создаёт inventory-service атомарно; здесь фиксируется результат.
     * APPROVED: всё или ничего. PARTIALLY_AVAILABLE: резервируется доступное (кладовщик может уменьшить количества).
     * S2: вызов inventory идёт между двумя короткими транзакциями; резерв идемпотентен (ключ от заявки и количеств).
     */
    public RequestResponse reserve(UUID id, ReserveDto dto, Actor actor) {
        return ops.<ReservePlan, Map<UUID, BigDecimal>, RequestResponse>run(id, OPERATION_TTL,
                r -> prepareReserve(r, dto),
                plan -> reserveInInventory(id, plan.number(), plan.warehouseId(), plan.lines(), plan.partial()),
                (r, plan, reserved) -> finishReserve(r, plan, reserved, actor));
    }

    private ReservePlan prepareReserve(Request r, ReserveDto dto) {
        if (r.getStatus() != RequestStatus.APPROVED && r.getStatus() != RequestStatus.PARTIALLY_AVAILABLE) {
            throw ApiException.conflict("INVALID_STATUS", "Резервирование недоступно в статусе " + r.getStatus());
        }
        boolean partial = r.getStatus() == RequestStatus.PARTIALLY_AVAILABLE;

        // Решение кладовщика проверяется сейчас, а в позиции записывается при фиксации результата
        Map<UUID, BigDecimal> approvals = new HashMap<>();
        if (dto != null && dto.items() != null && !dto.items().isEmpty()) {
            if (!partial) {
                throw ApiException.validation("Количества можно корректировать только при частичном обеспечении");
            }
            Map<UUID, RequestItem> byId = r.getItems().stream().collect(Collectors.toMap(RequestItem::getId, i -> i));
            for (ItemApproval a : dto.items()) {
                RequestItem item = byId.get(a.itemId());
                if (item == null) throw ApiException.validation("Позиции нет в заявке: " + a.itemId());
                if (a.approvedQuantity().compareTo(item.getRequestedQuantity()) > 0) {
                    throw ApiException.validation("Подтверждаемое количество больше запрошенного: позиция " + item.getLineNo());
                }
                QuantityRules.require(item.getUnitCode(), item.isUnitAllowFractions(),
                        a.approvedQuantity(), "Позиция " + item.getLineNo());
                approvals.put(item.getId(), a.approvedQuantity());
            }
        }
        List<ReserveLine> lines = new ArrayList<>();
        for (RequestItem i : r.getItems()) {
            BigDecimal approved = approvals.containsKey(i.getId()) ? approvals.get(i.getId()) : i.getApprovedQuantity();
            if (approved != null && approved.signum() > 0) {
                lines.add(new ReserveLine(i.getId(), i.getProductId(), approved));
            }
        }
        if (lines.isEmpty()) {
            throw ApiException.conflict("NOTHING_TO_RESERVE", "Нет позиций к резервированию: отклоните заявку");
        }
        return new ReservePlan(partial, r.getNumber(), r.getWarehouseId(), List.copyOf(lines), approvals);
    }

    private RequestResponse finishReserve(Request r, ReservePlan plan, Map<UUID, BigDecimal> reserved, Actor actor) {
        RequestStatus expected = plan.partial() ? RequestStatus.PARTIALLY_AVAILABLE : RequestStatus.APPROVED;
        requireStatus(r, expected);
        BigDecimal total = reserved.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw ApiException.conflict("NOTHING_RESERVED", "Остатка для резерва нет: повторите проверку наличия или отклоните заявку");
        }
        for (RequestItem item : r.getItems()) {
            if (plan.approvals().containsKey(item.getId())) item.setApprovedQuantity(plan.approvals().get(item.getId()));
            item.setReservedQuantity(reserved.getOrDefault(item.getId(), BigDecimal.ZERO));
        }
        transition(r, plan.partial() ? RequestStatus.RESERVED_PARTIAL : RequestStatus.RESERVED, actor.userId(),
                "Зарезервировано: " + total.stripTrailingZeros().toPlainString());
        log.info("AUDIT: резерв по заявке {} ({}), итого {}, кладовщик {}", r.getNumber(), r.getStatus(), total, actor.userId());
        return RequestMapper.toResponse(r);
    }

    // ================= отмена и закрытие =================

    private record CancelPlan(RequestStatus status, boolean release, List<ShortageMark> marks) {}

    /**
     * Отмена. Пользователь отменяет свою заявку до резерва (SUBMITTED, PARTIALLY_AVAILABLE, APPROVED);
     * кладовщик на любом этапе до выдачи, при этом резерв освобождается в inventory-service.
     * Собранное не отменяется (ТЗ): из PICKED отмены нет, а из READY_FOR_ISSUE она разрешена только если
     * по заявке ничего не собрано (выдавать нечего, иначе заявка зависла бы с резервом навсегда).
     * Если к моменту отмены что-то уже собрано (в открытых или завершённых заданиях), публикуется событие
     * picking.return_required со списком строк: товар лежит вне секций и его нужно вернуть вручную.
     * S2: снятие резерва идёт между двумя короткими транзакциях; пока оно выполняется, по заявке не пройдёт
     * ни сбор, ни другие операции (аренда), поэтому недобор в отметках не устареет.
     */
    public RequestResponse cancel(UUID id, String reason, Actor actor) {
        return ops.<CancelPlan, ReleaseResponse, RequestResponse>run(id, OPERATION_TTL,
                r -> prepareCancel(r, actor),
                plan -> plan.release() ? inventoryClient.release(id, plan.marks()) : null,
                (r, plan, released) -> finishCancel(r, plan, released, reason, actor));
    }

    private CancelPlan prepareCancel(Request r, Actor actor) {
        if (!actor.storekeeper() && !r.getRequesterId().equals(actor.userId())) {
            throw ApiException.notFound("REQUEST_NOT_FOUND", "Заявка не найдена"); // чужая заявка не раскрывается
        }
        Set<RequestStatus> allowed = actor.storekeeper()
                ? EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.APPROVED, RequestStatus.PARTIALLY_AVAILABLE,
                RequestStatus.RESERVED, RequestStatus.RESERVED_PARTIAL, RequestStatus.PICKING,
                RequestStatus.PICKING_WITH_SHORTAGE, RequestStatus.READY_FOR_ISSUE)
                : EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.APPROVED, RequestStatus.PARTIALLY_AVAILABLE);
        if (!allowed.contains(r.getStatus())) {
            throw ApiException.conflict("CANCEL_NOT_ALLOWED", "Отмена недоступна в статусе " + r.getStatus());
        }
        if (r.getStatus() == RequestStatus.READY_FOR_ISSUE && !nothingPicked(r.getId())) {
            throw ApiException.conflict("CANCEL_NOT_ALLOWED",
                    "Часть товара уже собрана: оформите выдачу, отмена недоступна");
        }
        if (RESERVED_STATES.contains(r.getStatus())) {
            // B1: недобор, зафиксированный при сборе, передаётся в inventory: это недостача (на проверку), а не свободный остаток
            List<PickingTask> tasks = pickingTaskRepository.findByRequestIdAndStatusIn(r.getId(),
                    List.of(PickingTask.Status.NEW, PickingTask.Status.IN_PROGRESS, PickingTask.Status.COMPLETED));
            return new CancelPlan(r.getStatus(), true, IssuingService.shortageMarks(tasks));
        }
        // B5: в APPROVED и PARTIALLY_AVAILABLE резерва быть не должно, но он мог остаться после сбоя
        boolean orphanCapable = r.getStatus() == RequestStatus.APPROVED || r.getStatus() == RequestStatus.PARTIALLY_AVAILABLE;
        return new CancelPlan(r.getStatus(), orphanCapable, List.of());
    }

    private RequestResponse finishCancel(Request r, CancelPlan plan, ReleaseResponse released, String reason, Actor actor) {
        requireStatus(r, plan.status());
        if (RESERVED_STATES.contains(r.getStatus())) {
            r.getItems().forEach(i -> i.setReservedQuantity(BigDecimal.ZERO));
            List<String> toReturn = new ArrayList<>(); // S3: уже собранное физически лежит вне секций
            // Завершённые задания (COMPLETED) тоже учитываются: после завершения сборки с недостачей
            // (PICKING_WITH_SHORTAGE) весь собранный товар лежит именно в них. Отклоняются только открытые.
            for (PickingTask t : pickingTaskRepository.findByRequestIdAndStatusIn(r.getId(),
                    List.of(PickingTask.Status.NEW, PickingTask.Status.IN_PROGRESS, PickingTask.Status.COMPLETED))) {
                for (PickingItem pi : t.getItems()) {
                    if (pi.getPickedQuantity() != null && pi.getPickedQuantity().signum() > 0) {
                        toReturn.add(pi.getSectionCode() + " / " + pi.getRequestItem().getProductSku() + ": "
                                + pi.getPickedQuantity().stripTrailingZeros().toPlainString());
                    }
                }
                if (t.getStatus() == PickingTask.Status.COMPLETED) continue; // закрытое задание остаётся как есть
                t.reject(); // открытое задание отклоняется, его работники снимаются
                outbox.audit(SERVICE, "picking.task_rejected", "PICKING_TASK", t.getId(), actor.userId(), details(
                        "taskNumber", t.getNumber(),
                        "requestId", r.getId(),
                        "requestNumber", r.getNumber(),
                        "reason", reason.trim()));
            }
            if (!toReturn.isEmpty()) {
                log.warn("Заявка {} отменена после частичной сборки: нужно вернуть товар в секции: {}", r.getNumber(), toReturn);
                outbox.audit(SERVICE, "picking.return_required", "REQUEST", r.getId(), actor.userId(), details(
                        "requestNumber", r.getNumber(),
                        "status", r.getStatus().name(),
                        "rows", toReturn.size(),
                        "items", toReturn,
                        "reason", reason.trim()));
            }
        } else {
            recordOrphanRelease(r, released, actor);
        }
        transition(r, RequestStatus.CANCELLED, actor.userId(), reason.trim());
        r.setStatusReason(reason.trim());
        log.info("AUDIT: отменена заявка {}, причина: {}, инициатор {}", r.getNumber(), reason, actor.userId());
        return RequestMapper.toResponse(r);
    }

    /** По заявке (в завершённом задании комплектации) не собрано ни одной единицы. */
    private boolean nothingPicked(UUID requestId) {
        return pickingTaskRepository.pickedTotals(requestId, PickingTask.Status.COMPLETED).stream()
                .noneMatch(t -> t.getPicked() != null && t.getPicked().signum() > 0);
    }

    /** Закрытие завершённой заявки (REJECTED, CANCELLED; ISSUED закрывается автоматически в части 5б). */
    @Transactional
    public RequestResponse close(UUID id, Actor actor) {
        Request r = lockAny(id);
        transition(r, RequestStatus.CLOSED, actor.userId(), null);
        r.setClosedAt(OffsetDateTime.now());
        return RequestMapper.toResponse(r);
    }

    // ================= чтение =================

    @Transactional(readOnly = true)
    public RequestResponse get(UUID id, Actor actor) {
        return RequestMapper.toResponse(findVisible(id, actor));
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> history(UUID id, Actor actor) {
        findVisible(id, actor);
        return historyRepository.findByRequestIdOrderByCreatedAtAsc(id).stream().map(RequestMapper::toHistory).toList();
    }

    /** Пользователь видит только свои заявки, кладовщик все. */
    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> search(RequestStatus status, UUID warehouseId, UUID requesterId, String text,
                                               int page, int size, Actor actor) {
        return search(status, warehouseId, requesterId, text, page, size, RequestSort.NEWEST, actor);
    }

    /** M7: priority влияет на очередь при sort = PRIORITY (срочные выше, при равенстве раньше поданные). */
    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> search(RequestStatus status, UUID warehouseId, UUID requesterId, String text,
                                               int page, int size, RequestSort sort, Actor actor) {
        Sort order = sort == RequestSort.PRIORITY
                ? Sort.by(Sort.Order.desc("priority"), Sort.Order.asc("submittedAt").nullsLast(),
                        Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
                : Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), order);
        Specification<Request> spec = (root, query, cb) -> cb.conjunction();
        if (!actor.seesAll()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("requesterId"), actor.userId()));
        } else if (requesterId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("requesterId"), requesterId));
        }
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (warehouseId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("warehouseId"), warehouseId));
        if (text != null && !text.isBlank()) {
            String escaped = text.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("number")), "%" + escaped + "%", '\\'));
        }
        return PageResponse.from(requestRepository.findAll(spec, pageable).map(RequestMapper::toSummary));
    }

    // ================= вспомогательное =================

    /**
     * Вызов inventory-service и перенос результата в позиции. Ключ идемпотентности зависит от заявки и количеств:
     * повтор того же действия после сбоя вернёт сохранённый ответ, а не второй резерв.
     * Если резерв уже создан (сбой после ответа inventory), подхватываем существующие резервы.
     */
    private Map<UUID, BigDecimal> reserveInInventory(UUID requestId, String number, UUID warehouseId,
                                                     List<ReserveLine> lines, boolean partial) {
        try {
            ReserveResponse response = inventoryClient.reserve(
                    new ReserveRequest(requestId, warehouseId, partial, lines), idempotencyKey(requestId, lines, partial));
            Map<UUID, BigDecimal> reserved = new HashMap<>();
            response.items().forEach(i -> reserved.put(i.requestItemId(), i.reserved()));
            return reserved;
        } catch (ApiException e) {
            if (!"RESERVATION_EXISTS".equals(e.getCode())) throw e;
            Map<UUID, BigDecimal> adopted = new HashMap<>();
            for (ReservationDto d : inventoryClient.reservations(requestId)) {
                if ("ACTIVE".equals(d.status())) adopted.merge(d.requestItemId(), d.quantity(), BigDecimal::add);
            }
            if (adopted.isEmpty()) throw e;
            log.warn("Резерв по заявке {} уже существовал в inventory: подхвачен без повторного создания", number);
            return adopted;
        }
    }

    private static String idempotencyKey(UUID requestId, List<ReserveLine> lines, boolean partial) {
        StringBuilder raw = new StringBuilder(requestId.toString()).append('|').append(partial);
        for (ReserveLine l : lines) {
            raw.append('|').append(l.requestItemId()).append(':').append(l.quantity().stripTrailingZeros().toPlainString());
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            return "rsv-" + HexFormat.of().formatHex(hash).substring(0, 32); // 36 символов: допустимый формат ключа
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Единственное место смены статуса: проверка перехода и запись в историю. */
    public void transition(Request r, RequestStatus to, UUID actorId, String comment) {
        RequestStatus from = r.getStatus();
        if (!from.canTransitionTo(to)) {
            throw ApiException.conflict("INVALID_TRANSITION", "Переход " + from + " → " + to + " не допускается");
        }
        r.setStatus(to);
        r.touch();
        StatusHistory h = new StatusHistory();
        h.setRequestId(r.getId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActorId(actorId);
        h.setComment(comment);
        historyRepository.save(h);
        outbox.audit(SERVICE, "request.status_changed", "REQUEST", r.getId(), actorId, details(
                "requestNumber", r.getNumber(),
                "requesterId", r.getRequesterId(),
                "warehouseId", r.getWarehouseId(),
                "fromStatus", from.name(),
                "newStatus", to.name(),
                "actorId", actorId, // для уведомлений: не сообщать человеку о его же действии
                "itemsCount", r.getItems().size(),
                "requestedQuantity", total(r, RequestItem::getRequestedQuantity),
                "approvedQuantity", total(r, RequestItem::getApprovedQuantity),
                "reservedQuantity", total(r, RequestItem::getReservedQuantity),
                "issuedQuantity", total(r, RequestItem::getIssuedQuantity),
                "issueNumber", r.getIssueNumber(),
                "createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().toString()));
    }

    private Request lockAny(UUID id) {
        Request r = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Заявка не найдена"));
        ops.assertFree(id); // S2: пока идёт операция с внешним вызовом, остальные изменения по заявке отклоняются
        return r;
    }

    /** Своя заявка (чужая выглядит как несуществующая) с блокировкой. */
    private Request lockOwn(UUID id, Actor actor) {
        Request r = lockAny(id);
        if (!r.getRequesterId().equals(actor.userId())) {
            throw ApiException.notFound("REQUEST_NOT_FOUND", "Заявка не найдена");
        }
        return r;
    }

    private Request findVisible(UUID id, Actor actor) {
        Request r = requestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Заявка не найдена"));
        if (!actor.seesAll() && !r.getRequesterId().equals(actor.userId())) {
            throw ApiException.notFound("REQUEST_NOT_FOUND", "Заявка не найдена");
        }
        return r;
    }

    private static void requireStatus(Request r, RequestStatus expected) {
        if (r.getStatus() != expected) {
            throw ApiException.conflict("INVALID_STATUS",
                    "Операция допустима для статуса " + expected + ", текущий статус: " + r.getStatus());
        }
    }

    private static Set<UUID> productIds(Request r) {
        return r.getItems().stream().map(RequestItem::getProductId).collect(Collectors.toSet());
    }

    private Map<UUID, Product> loadProducts(Set<UUID> ids) {
        Map<UUID, Product> found = catalogClient.products(ids);
        for (UUID id : ids) {
            Product p = found.get(id);
            if (p == null) throw ApiException.validation("Номенклатура не найдена: " + id);
            if (!p.active()) throw ApiException.validation("Номенклатура в архиве: " + p.sku());
        }
        return found;
    }

    /** Заменяет позиции. Сначала удаляем старые и сбрасываем в БД: иначе INSERT нового line_no опередит DELETE. */
    private void fillItems(Request r, List<ItemInput> inputs, Map<UUID, Product> products) {
        r.getItems().clear();
        if (r.getId() != null) requestRepository.flush();

        int line = 1;
        for (ItemInput in : inputs) {
            Product p = products.get(in.productId());
            boolean allowFractions = p.unitAllowFractions() == null || p.unitAllowFractions();
            QuantityRules.require(p.unitCode(), allowFractions, in.quantity(), "Позиция " + line);
            RequestItem item = new RequestItem();
            item.setRequest(r);
            item.setLineNo(line++);
            item.setProductId(p.id());
            item.setProductSku(p.sku());
            item.setProductName(p.name());
            item.setUnitCode(p.unitCode());
            item.setUnitAllowFractions(allowFractions);
            item.setRequestedQuantity(in.quantity());
            item.setComment(blankToNull(in.comment()));
            r.getItems().add(item);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Кладовщик проверил результаты комплектации и готов оформить выдачу (разд. 13 ТЗ). */
    @Transactional
    public RequestResponse readyForIssue(UUID id, String comment, Actor actor) {
        Request r = lockAny(id);
        if ((r.getStatus() == RequestStatus.PICKED || r.getStatus() == RequestStatus.PICKING_WITH_SHORTAGE)
                && nothingPicked(r.getId())) {
            // иначе заявка попала бы в READY_FOR_ISSUE, из которого нечего выдавать
            throw ApiException.conflict("NOTHING_PICKED", "Ничего не собрано: оформлять выдачу нечего, отмените заявку");
        }
        transition(r, RequestStatus.READY_FOR_ISSUE, actor.userId(), blankToNull(comment));
        log.info("AUDIT: заявка {} готова к выдаче, кладовщик {}", r.getNumber(), actor.userId());
        return RequestMapper.toResponse(r);
    }

    /**
     * B5: в статусах APPROVED и PARTIALLY_AVAILABLE резерва быть не должно, но он мог остаться в inventory, если резерв
     * создан, а статус заявки сохранить не удалось (сбой, таймаут, перезапуск). Перед отменой или отклонением снимаем его
     * (операция идемпотентна). Если inventory недоступен, операция не выполняется: осиротевший резерв остался бы навсегда.
     * Вызывается между транзакциями (S2), поэтому работает с идентификатором, а не с сущностью.
     */
    private ReleaseResponse releaseOrphanedReservation(UUID requestId, RequestStatus status) {
        if (status != RequestStatus.APPROVED && status != RequestStatus.PARTIALLY_AVAILABLE) {
            return null;
        }
        return inventoryClient.release(requestId);
    }

    private void recordOrphanRelease(Request r, ReleaseResponse released, Actor actor) {
        if (released == null || released.releasedReservations() <= 0) return;
        log.warn("Заявка {}: снят осиротевший резерв ({} записей, {})", r.getNumber(),
                released.releasedReservations(), released.releasedQuantity());
        r.getItems().forEach(i -> i.setReservedQuantity(BigDecimal.ZERO));
        outbox.audit(SERVICE, "request.orphan_reservation_released", "REQUEST", r.getId(), actor.userId(), details(
                "requestNumber", r.getNumber(),
                "status", r.getStatus().name(),
                "releasedReservations", released.releasedReservations(),
                "releasedQuantity", released.releasedQuantity()));
    }
}
