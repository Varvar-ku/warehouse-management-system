// AdjustmentService.java
package ru.warehouse.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.common.event.OutboxPublisher;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.common.numbering.DocumentNumbers;
import ru.warehouse.inventory.dto.AdjustmentDtos.AdjustmentItemRequest;
import ru.warehouse.inventory.dto.AdjustmentDtos.AdjustmentResponse;
import ru.warehouse.inventory.dto.AdjustmentDtos.CreateAdjustmentRequest;
import ru.warehouse.inventory.entity.*;
import ru.warehouse.inventory.repository.StockAdjustmentRepository;
import ru.warehouse.inventory.repository.StockMovementRepository;
import ru.warehouse.inventory.repository.StockRepository;
import ru.warehouse.inventory.repository.StockShortageRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static ru.warehouse.common.event.OutboxPublisher.details;

/**
 * Корректировка остатка по подтверждённым недостачам (B1). Работает по образцу списания:
 * кладовщик создаёт черновик из открытых недостач, администратор одобряет (не автор документа),
 * кладовщик проводит: остаток уменьшается, пишется движение ADJUSTMENT, недостачи закрываются,
 * блокировка со строк остатков снимается.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdjustmentService {
    private final StockAdjustmentRepository adjustmentRepository;
    private final DocumentNumbers numbers;
    private final StockShortageRepository shortageRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final ShortageService shortageService;
    private final MovementEvents movementEvents;
    private final OutboxPublisher outbox;

    private static final String SERVICE = "inventory-service";

    @Transactional
    public AdjustmentResponse create(CreateAdjustmentRequest req, UUID actorId) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (AdjustmentItemRequest i : req.items()) {
            if (!ids.add(i.shortageId())) {
                throw ApiException.validation("Недостача указана дважды: " + i.shortageId());
            }
        }
        List<StockShortage> cases = shortageRepository.lockAllByIdIn(ids);
        if (cases.size() != ids.size()) {
            throw ApiException.validation("Недостача не найдена");
        }
        StockAdjustment a = new StockAdjustment();
        a.setNumber(numbers.next("AD"));
        a.setWarehouseId(req.warehouseId());
        a.setCreatedBy(actorId);
        a.setComment(req.comment().trim());
        for (StockShortage s : cases) {
            if (!s.getWarehouseId().equals(req.warehouseId())) {
                throw ApiException.validation("Недостача " + s.getNumber() + " относится к другому складу");
            }
            if (s.getStatus() != StockShortage.Status.OPEN) {
                throw ApiException.conflict("SHORTAGE_NOT_OPEN", "Недостача " + s.getNumber() + " уже закрыта");
            }
            // S6 (вариант 2): корректировку по недостаче создаёт не тот, кто её зафиксировал
            if (actorId.equals(s.getReportedBy())) {
                throw ApiException.forbidden("SELF_ADJUSTMENT", "Недостачу " + s.getNumber()
                        + " зафиксировали вы: корректировку по ней создаёт другой кладовщик");
            }
            if (s.getAdjustmentId() != null) {
                throw ApiException.conflict("SHORTAGE_IN_ADJUSTMENT",
                        "Недостача " + s.getNumber() + " уже включена в другой документ корректировки");
            }
            StockAdjustmentItem item = new StockAdjustmentItem();
            item.setAdjustment(a);
            item.setShortage(s);
            item.setStock(s.getStock());
            item.setProductId(s.getProductId());
            item.setQuantity(s.getQuantity());
            a.getItems().add(item);
        }
        a = adjustmentRepository.save(a);
        for (StockShortage s : cases) s.setAdjustmentId(a.getId());
        outbox.audit(SERVICE, "adjustment.created", "STOCK_ADJUSTMENT", a.getId(), actorId, details(
                "number", a.getNumber(),
                "warehouseId", a.getWarehouseId(),
                "itemsCount", a.getItems().size(),
                "comment", a.getComment()));
        return ShortageMapper.toResponse(a);
    }

    /** Одобрение администратором. Автор документа одобрить его не может. Повторный вызов безопасен. */
    @Transactional
    public AdjustmentResponse approve(UUID id, UUID approverId) {
        StockAdjustment a = lock(id);
        if (a.getStatus() != StockAdjustment.Status.DRAFT) {
            throw ApiException.conflict("INVALID_STATUS", "Одобрять можно только черновик");
        }
        if (a.getCreatedBy().equals(approverId)) {
            throw ApiException.forbidden("SELF_APPROVAL", "Корректировку не может одобрить её автор");
        }
        // S6 (вариант 2): одобряет не тот, кто зафиксировал недостачу
        for (StockAdjustmentItem item : a.getItems()) {
            if (approverId.equals(item.getShortage().getReportedBy())) {
                throw ApiException.forbidden("SELF_APPROVAL", "Недостачу " + item.getShortage().getNumber()
                        + " зафиксировали вы: корректировку не может одобрить тот, кто её зафиксировал");
            }
        }
        if (a.getApprovedAt() == null) {
            a.setApprovedBy(approverId);
            a.setApprovedAt(OffsetDateTime.now());
            outbox.audit(SERVICE, "adjustment.approved", "STOCK_ADJUSTMENT", a.getId(), approverId, details(
                    "number", a.getNumber()));
        }
        return ShortageMapper.toResponse(a);
    }

    /** Проведение: повторная проверка под блокировкой, уменьшение остатка, движения ADJUSTMENT, закрытие недостач. */
    @Transactional
    public AdjustmentResponse confirm(UUID id, UUID actorId) {
        StockAdjustment a = lock(id);
        if (a.getStatus() != StockAdjustment.Status.DRAFT) {
            throw ApiException.conflict("INVALID_STATUS", "Документ в статусе " + a.getStatus() + " подтвердить нельзя");
        }
        if (a.getApprovedAt() == null) {
            throw ApiException.conflict("APPROVAL_REQUIRED", "Требуется одобрение администратора");
        }
        Map<UUID, BigDecimal> perStock = new LinkedHashMap<>();
        for (StockAdjustmentItem item : a.getItems()) {
            if (item.getShortage().getStatus() != StockShortage.Status.OPEN) {
                throw ApiException.conflict("SHORTAGE_NOT_OPEN",
                        "Недостача " + item.getShortage().getNumber() + " уже закрыта");
            }
            perStock.merge(item.getStock().getId(), item.getQuantity(), BigDecimal::add);
        }
        Map<UUID, Stock> stocks = stockRepository.lockAll(perStock.keySet());

        // после уменьшения остатка резервы других заявок по строке должны оставаться покрытыми
        List<String> problems = new ArrayList<>();
        perStock.forEach((stockId, qty) -> {
            Stock s = stocks.get(stockId);
            if (s.available().compareTo(qty) < 0) {
                problems.add("Остаток " + s.getSectionCode() + " / " + s.getBatch().getBatchNumber() + ": доступно "
                        + s.available().toPlainString() + " (за вычетом резервов), к списанию " + qty.toPlainString());
            }
        });
        if (!problems.isEmpty()) {
            throw ApiException.conflict("INSUFFICIENT_AVAILABLE_STOCK",
                    "Остаток уже зарезервирован другими заявками на недостающее количество", problems);
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (StockAdjustmentItem item : a.getItems()) {
            Stock s = stocks.get(item.getStock().getId());
            StockShortage sh = item.getShortage();
            s.setQuantity(s.getQuantity().subtract(item.getQuantity()));

            StockMovement m = new StockMovement();
            m.setMovementType(StockMovement.Type.ADJUSTMENT);
            m.setWarehouseId(a.getWarehouseId());
            m.setProductId(item.getProductId());
            m.setBatchId(s.getBatch().getId());
            m.setFromSectionId(s.getSectionId());
            m.setFromSectionCode(s.getSectionCode());
            m.setQuantity(item.getQuantity());
            m.setUnitCode(s.getUnitCode());
            m.setDocumentType("ADJUSTMENT");
            m.setDocumentId(a.getId());
            m.setPerformedBy(actorId);
            m.setPerformedAt(now);
            m.setComment("Корректировка " + a.getNumber() + ", недостача " + sh.getNumber() + ": " + a.getComment());
            movementRepository.save(m);
            movementEvents.publish(m, s.getBatch(), a.getNumber(), details(
                    "reason", "SHORTAGE",
                    "shortageNumber", sh.getNumber(),
                    "requestId", sh.getRequestId(),
                    "comment", a.getComment(),
                    "createdBy", a.getCreatedBy(),
                    "approvedBy", a.getApprovedBy()));

            sh.setStatus(StockShortage.Status.RESOLVED);
            sh.setResolution(StockShortage.Resolution.WRITTEN_OFF);
            sh.setResolutionComment(a.getComment());
            sh.setResolvedBy(actorId);
            sh.setResolvedAt(now);
        }
        a.setStatus(StockAdjustment.Status.CONFIRMED);
        a.setConfirmedBy(actorId);
        a.setConfirmedAt(now);
        shortageService.releaseHoldIfResolved(stocks.values());
        outbox.audit(SERVICE, "adjustment.confirmed", "STOCK_ADJUSTMENT", a.getId(), actorId, details(
                "number", a.getNumber(),
                "itemsCount", a.getItems().size(),
                "approvedBy", a.getApprovedBy()));
        return ShortageMapper.toResponse(a);
    }

    /** Отмена черновика: недостачи снова открыты для другого решения, блокировка строк остаётся. */
    @Transactional
    public AdjustmentResponse cancel(UUID id, String reason, UUID actorId) {
        StockAdjustment a = lock(id);
        if (a.getStatus() != StockAdjustment.Status.DRAFT) {
            throw ApiException.conflict("INVALID_STATUS", "Отменить можно только черновик");
        }
        a.setStatus(StockAdjustment.Status.CANCELLED);
        a.setCancelledBy(actorId);
        a.setCancelReason(reason.trim());
        for (StockAdjustmentItem item : a.getItems()) item.getShortage().setAdjustmentId(null);
        outbox.audit(SERVICE, "adjustment.cancelled", "STOCK_ADJUSTMENT", a.getId(), actorId, details(
                "number", a.getNumber(),
                "cancelReason", reason.trim()));
        return ShortageMapper.toResponse(a);
    }

    @Transactional(readOnly = true)
    public AdjustmentResponse get(UUID id) {
        return ShortageMapper.toResponse(adjustmentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ADJUSTMENT_NOT_FOUND", "Корректировка не найдена")));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdjustmentResponse> search(StockAdjustment.Status status, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Specification<StockAdjustment> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        return PageResponse.from(adjustmentRepository.findAll(spec, pageable).map(ShortageMapper::toResponse));
    }

    private StockAdjustment lock(UUID id) {
        return adjustmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("ADJUSTMENT_NOT_FOUND", "Корректировка не найдена"));
    }
}
