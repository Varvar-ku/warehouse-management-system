// RequestService.java
package ru.warehouse.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.common.exception.ApiException;
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

/** Жизненный цикл заявки до резервирования (разд. 5.4, 8 и 11 ТЗ). Комплектация и выдача: часть 5б. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestService {
    private static final Set<RequestStatus> RESERVED_STATES = EnumSet.of(RequestStatus.RESERVED,
            RequestStatus.RESERVED_PARTIAL, RequestStatus.PICKING, RequestStatus.PICKING_WITH_SHORTAGE);

    private final RequestRepository requestRepository;
    private final StatusHistoryRepository historyRepository;
    private final CatalogClient catalogClient;
    private final WarehouseClient warehouseClient;
    private final InventoryClient inventoryClient;

    // ================= пользователь: черновик и отправка =================

    @Transactional
    public RequestResponse create(CreateRequestDto dto, Actor actor) {
        WarehouseInfo warehouse = warehouseClient.warehouse(dto.warehouseId());
        if (!warehouse.active()) {
            throw ApiException.conflict("WAREHOUSE_INACTIVE", "Склад отключён");
        }
        Request r = new Request();
        r.setNumber(String.format("RQ-%d-%06d", OffsetDateTime.now().getYear(), requestRepository.nextNumber()));
        r.setRequesterId(actor.userId());
        r.setWarehouseId(warehouse.id());
        r.setPriority(dto.priority() == null ? 0 : dto.priority());
        r.setComment(blankToNull(dto.comment()));
        fillItems(r, dto.items());
        r = requestRepository.save(r);
        log.info("AUDIT: создана заявка {} ({}), пользователь {}", r.getNumber(), r.getId(), actor.userId());
        return RequestMapper.toResponse(r);
    }

    @Transactional
    public RequestResponse update(UUID id, UpdateRequestDto dto, Actor actor) {
        Request r = lockOwn(id, actor);
        requireStatus(r, RequestStatus.DRAFT);
        if (dto.priority() != null) r.setPriority(dto.priority());
        if (dto.comment() != null) r.setComment(blankToNull(dto.comment()));
        if (dto.items() != null) {
            if (dto.items().isEmpty()) throw ApiException.validation("Список позиций не может быть пустым");
            fillItems(r, dto.items());
        }
        r.touch();
        return RequestMapper.toResponse(r);
    }

    /** Удаление черновика (разд. 8 ТЗ). Истории у черновика ещё нет, поэтому физическое удаление допустимо. */
    @Transactional
    public void deleteDraft(UUID id, Actor actor) {
        Request r = lockOwn(id, actor);
        requireStatus(r, RequestStatus.DRAFT);
        requestRepository.delete(r);
        log.info("AUDIT: удалён черновик заявки {}, пользователь {}", r.getNumber(), actor.userId());
    }

    @Transactional
    public RequestResponse submit(UUID id, Actor actor) {
        Request r = lockOwn(id, actor);
        requireStatus(r, RequestStatus.DRAFT);
        if (r.getItems().isEmpty()) throw ApiException.validation("В заявке нет позиций");
        loadProducts(productIds(r)); // номенклатура должна существовать и не быть в архиве (разд. 11 ТЗ)
        transition(r, RequestStatus.SUBMITTED, actor.userId(), null);
        r.setSubmittedAt(OffsetDateTime.now());
        log.info("AUDIT: отправлена заявка {} ({}), пользователь {}", r.getNumber(), r.getId(), actor.userId());
        return RequestMapper.toResponse(r);
    }

    // ================= кладовщик: проверка наличия, решение, резерв =================

    /**
     * Проверка наличия: вход в UNDER_REVIEW, расчёт доступного количества по каждой позиции
     * и итог APPROVED (всё доступно) или PARTIALLY_AVAILABLE. Допустима и как повторная проверка.
     */
    @Transactional
    public RequestResponse review(UUID id, Actor actor) {
        Request r = lockAny(id);
        if (!EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.APPROVED, RequestStatus.PARTIALLY_AVAILABLE)
                .contains(r.getStatus())) {
            throw ApiException.conflict("INVALID_STATUS", "Проверка наличия недоступна в статусе " + r.getStatus());
        }
        transition(r, RequestStatus.UNDER_REVIEW, actor.userId(), "Проверка наличия");
        r.setReviewedBy(actor.userId());
        r.setReviewedAt(OffsetDateTime.now());

        // Спрос по номенклатуре суммируем: inventory принимает каждую номенклатуру один раз
        Map<UUID, BigDecimal> demand = new LinkedHashMap<>();
        for (RequestItem i : r.getItems()) demand.merge(i.getProductId(), i.getRequestedQuantity(), BigDecimal::add);
        List<AvailabilityLine> lines = demand.entrySet().stream()
                .map(e -> new AvailabilityLine(e.getKey(), e.getValue())).toList();
        Map<UUID, BigDecimal> available = new HashMap<>();
        for (AvailabilityItem a : inventoryClient.availability(r.getWarehouseId(), lines)) {
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
        transition(r, everythingAvailable ? RequestStatus.APPROVED : RequestStatus.PARTIALLY_AVAILABLE,
                actor.userId(), everythingAvailable ? "Все позиции доступны" : "Доступна только часть");
        log.info("AUDIT: проверка наличия по заявке {}: {}, кладовщик {}", r.getNumber(), r.getStatus(), actor.userId());
        return RequestMapper.toResponse(r);
    }

    @Transactional
    public RequestResponse reject(UUID id, String reason, Actor actor) {
        Request r = lockAny(id);
        transition(r, RequestStatus.REJECTED, actor.userId(), reason.trim());
        r.setStatusReason(reason.trim());
        log.info("AUDIT: отклонена заявка {}, причина: {}, кладовщик {}", r.getNumber(), reason, actor.userId());
        return RequestMapper.toResponse(r);
    }

    /**
     * Резервирование (разд. 11 ТЗ). Резерв создаёт inventory-service атомарно; здесь фиксируется результат.
     * APPROVED: всё или ничего. PARTIALLY_AVAILABLE: резервируется доступное (кладовщик может уменьшить количества).
     */
    @Transactional
    public RequestResponse reserve(UUID id, ReserveDto dto, Actor actor) {
        Request r = lockAny(id);
        if (r.getStatus() != RequestStatus.APPROVED && r.getStatus() != RequestStatus.PARTIALLY_AVAILABLE) {
            throw ApiException.conflict("INVALID_STATUS", "Резервирование недоступно в статусе " + r.getStatus());
        }
        boolean partial = r.getStatus() == RequestStatus.PARTIALLY_AVAILABLE;

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
                item.setApprovedQuantity(a.approvedQuantity());
            }
        }

        List<ReserveLine> lines = r.getItems().stream()
                .filter(i -> i.getApprovedQuantity() != null && i.getApprovedQuantity().signum() > 0)
                .map(i -> new ReserveLine(i.getId(), i.getProductId(), i.getApprovedQuantity())).toList();
        if (lines.isEmpty()) {
            throw ApiException.conflict("NOTHING_TO_RESERVE", "Нет позиций к резервированию: отклоните заявку");
        }

        Map<UUID, BigDecimal> reserved = reserveInInventory(r, lines, partial);
        BigDecimal total = reserved.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw ApiException.conflict("NOTHING_RESERVED", "Остатка для резерва нет: повторите проверку наличия или отклоните заявку");
        }
        for (RequestItem item : r.getItems()) {
            item.setReservedQuantity(reserved.getOrDefault(item.getId(), BigDecimal.ZERO));
        }
        transition(r, partial ? RequestStatus.RESERVED_PARTIAL : RequestStatus.RESERVED, actor.userId(),
                "Зарезервировано: " + total.stripTrailingZeros().toPlainString());
        log.info("AUDIT: резерв по заявке {} ({}), итого {}, кладовщик {}", r.getNumber(), r.getStatus(), total, actor.userId());
        return RequestMapper.toResponse(r);
    }

    // ================= отмена и закрытие =================

    /**
     * Отмена. Пользователь отменяет свою заявку до резерва (SUBMITTED, PARTIALLY_AVAILABLE, APPROVED);
     * кладовщик на любом этапе до выдачи, при этом резерв освобождается в inventory-service.
     */
    @Transactional
    public RequestResponse cancel(UUID id, String reason, Actor actor) {
        Request r = actor.storekeeper() ? lockAny(id) : lockOwn(id, actor);
        Set<RequestStatus> allowed = actor.storekeeper()
                ? EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.APPROVED, RequestStatus.PARTIALLY_AVAILABLE,
                RequestStatus.RESERVED, RequestStatus.RESERVED_PARTIAL, RequestStatus.PICKING,
                RequestStatus.PICKING_WITH_SHORTAGE)
                : EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.APPROVED, RequestStatus.PARTIALLY_AVAILABLE);
        if (!allowed.contains(r.getStatus())) {
            throw ApiException.conflict("CANCEL_NOT_ALLOWED", "Отмена недоступна в статусе " + r.getStatus());
        }
        if (RESERVED_STATES.contains(r.getStatus())) {
            // Сначала освобождаем резерв: если inventory недоступен, статус не меняется и состояние остаётся согласованным
            inventoryClient.release(r.getId());
            r.getItems().forEach(i -> i.setReservedQuantity(BigDecimal.ZERO));
            // TODO часть 5б: при PICKING отменить и задания на комплектацию
        }
        transition(r, RequestStatus.CANCELLED, actor.userId(), reason.trim());
        r.setStatusReason(reason.trim());
        log.info("AUDIT: отменена заявка {}, причина: {}, инициатор {}", r.getNumber(), reason, actor.userId());
        return RequestMapper.toResponse(r);
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
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Specification<Request> spec = (root, query, cb) -> cb.conjunction();
        if (!actor.storekeeper()) {
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
    private Map<UUID, BigDecimal> reserveInInventory(Request r, List<ReserveLine> lines, boolean partial) {
        try {
            ReserveResponse response = inventoryClient.reserve(
                    new ReserveRequest(r.getId(), r.getWarehouseId(), partial, lines), idempotencyKey(r, lines, partial));
            Map<UUID, BigDecimal> reserved = new HashMap<>();
            response.items().forEach(i -> reserved.put(i.requestItemId(), i.reserved()));
            return reserved;
        } catch (ApiException e) {
            if (!"RESERVATION_EXISTS".equals(e.getCode())) throw e;
            Map<UUID, BigDecimal> adopted = new HashMap<>();
            for (ReservationDto d : inventoryClient.reservations(r.getId())) {
                if ("ACTIVE".equals(d.status())) adopted.merge(d.requestItemId(), d.quantity(), BigDecimal::add);
            }
            if (adopted.isEmpty()) throw e;
            log.warn("Резерв по заявке {} уже существовал в inventory: подхвачен без повторного создания", r.getNumber());
            return adopted;
        }
    }

    private static String idempotencyKey(Request r, List<ReserveLine> lines, boolean partial) {
        StringBuilder raw = new StringBuilder(r.getId().toString()).append('|').append(partial);
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
    private void transition(Request r, RequestStatus to, UUID actorId, String comment) {
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
    }

    private Request lockAny(UUID id) {
        return requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Заявка не найдена"));
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
        if (!actor.storekeeper() && !r.getRequesterId().equals(actor.userId())) {
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
    private void fillItems(Request r, List<ItemInput> inputs) {
        Map<UUID, Product> products = loadProducts(
                inputs.stream().map(ItemInput::productId).collect(Collectors.toSet()));
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
}