// WriteOffService.java
package ru.warehouse.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.inventory.config.WriteOffPolicy;
import ru.warehouse.inventory.dto.OperationDtos.*;
import ru.warehouse.inventory.entity.Stock;
import ru.warehouse.inventory.entity.StockMovement;
import ru.warehouse.inventory.entity.WriteOff;
import ru.warehouse.inventory.entity.WriteOffItem;
import ru.warehouse.inventory.repository.StockMovementRepository;
import ru.warehouse.inventory.repository.StockRepository;
import ru.warehouse.inventory.repository.WriteOffRepository;
import java.time.OffsetDateTime;
import java.util.*;

/** Списание (разд. 14 ТЗ). */
@Slf4j
@Service
@RequiredArgsConstructor
public class WriteOffService {
    private final WriteOffRepository writeOffRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final WriteOffPolicy policy;

    @Transactional
    public WriteOffResponse create(CreateWriteOffRequest req, UUID actorId) {
        if (policy.commentRequiredReasons().contains(req.reasonCode())
                && (req.comment() == null || req.comment().isBlank())) {
            throw ApiException.validation("Для причины " + req.reasonCode() + " комментарий обязателен");
        }
        Set<UUID> stockIds = new HashSet<>();
        for (WriteOffItemRequest i : req.items()) {
            if (!stockIds.add(i.stockId())) {
                throw ApiException.validation("Остаток указан дважды: " + i.stockId());
            }
        }
        Map<UUID, Stock> stocks = new HashMap<>();
        stockRepository.findAllById(stockIds).forEach(s -> stocks.put(s.getId(), s));

        WriteOff w = new WriteOff();
        w.setNumber(String.format("WO-%d-%06d", OffsetDateTime.now().getYear(), writeOffRepository.nextNumber()));
        w.setWarehouseId(req.warehouseId());
        w.setCreatedBy(actorId);
        w.setReason(req.reasonCode());
        w.setComment(blankToNull(req.comment()));
        w.setApprovalRequired(policy.approvalReasons().contains(req.reasonCode()));

        for (WriteOffItemRequest i : req.items()) {
            Stock s = stocks.get(i.stockId());
            if (s == null) throw ApiException.validation("Остаток не найден: " + i.stockId());
            if (!s.getWarehouseId().equals(req.warehouseId())) {
                throw ApiException.validation("Остаток " + i.stockId() + " принадлежит другому складу");
            }
            QuantityRules.require(s.getUnitCode(), s.getBatch().isUnitAllowFractions(), i.quantity(), "Количество к списанию");
            if (s.available().compareTo(i.quantity()) < 0) { // предварительная проверка; окончательная при подтверждении
                throw ApiException.conflict("INSUFFICIENT_AVAILABLE_STOCK", "Остаток " + s.getSectionCode() + " / "
                        + s.getBatch().getBatchNumber() + ": доступно " + s.available().toPlainString()
                        + " (за вычетом резерва), к списанию " + i.quantity().toPlainString());
            }
            WriteOffItem item = new WriteOffItem();
            item.setWriteOff(w);
            item.setStock(s);
            item.setProductId(s.getProductId());
            item.setQuantity(i.quantity());
            w.getItems().add(item);
        }
        w = writeOffRepository.save(w);
        log.info("AUDIT: создано списание {} (причина {}, подтверждение администратора: {}), инициатор {}",
                w.getNumber(), w.getReason(), w.isApprovalRequired(), actorId);
        return OperationMapper.toResponse(w);
    }

    /** Подтверждение администратором (когда оно требуется политикой). Повторный вызов безопасен. */
    @Transactional
    public WriteOffResponse approve(UUID id, UUID adminId) {
        WriteOff w = writeOffRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("WRITE_OFF_NOT_FOUND", "Списание не найдено"));
        if (w.getStatus() != WriteOff.Status.DRAFT) {
            throw ApiException.conflict("INVALID_STATUS", "Подтверждать можно только черновик");
        }
        if (!w.isApprovalRequired()) {
            throw ApiException.conflict("APPROVAL_NOT_REQUIRED", "Для этого списания подтверждение администратора не требуется");
        }
        if (w.getApprovedAt() == null) {
            w.setApprovedBy(adminId);
            w.setApprovedAt(OffsetDateTime.now());
            log.info("AUDIT: администратор {} подтвердил списание {}", adminId, w.getNumber());
        }
        return OperationMapper.toResponse(w);
    }

    /** Проведение: повторная проверка доступного остатка под блокировкой, уменьшение остатка, движения WRITE_OFF. */
    @Transactional
    public WriteOffResponse confirm(UUID id, UUID actorId) {
        WriteOff w = writeOffRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("WRITE_OFF_NOT_FOUND", "Списание не найдено"));
        if (w.getStatus() != WriteOff.Status.DRAFT) {
            throw ApiException.conflict("INVALID_STATUS", "Документ в статусе " + w.getStatus() + " подтвердить нельзя");
        }
        if (w.isApprovalRequired() && w.getApprovedAt() == null) {
            throw ApiException.conflict("APPROVAL_REQUIRED", "Требуется подтверждение администратора");
        }
        Map<UUID, Stock> stocks = stockRepository.lockAll(
                w.getItems().stream().map(i -> i.getStock().getId()).toList());

        List<String> problems = new ArrayList<>();
        for (WriteOffItem item : w.getItems()) {
            Stock s = stocks.get(item.getStock().getId());
            if (s.available().compareTo(item.getQuantity()) < 0) {
                problems.add("Остаток " + s.getSectionCode() + " / " + s.getBatch().getBatchNumber() + ": доступно "
                        + s.available().toPlainString() + ", к списанию " + item.getQuantity().toPlainString());
            }
        }
        if (!problems.isEmpty()) {
            throw ApiException.conflict("INSUFFICIENT_AVAILABLE_STOCK", "Недостаточно доступного остатка", problems);
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (WriteOffItem item : w.getItems()) {
            Stock s = stocks.get(item.getStock().getId());
            s.setQuantity(s.getQuantity().subtract(item.getQuantity()));
            StockMovement m = new StockMovement();
            m.setMovementType(StockMovement.Type.WRITE_OFF);
            m.setWarehouseId(w.getWarehouseId());
            m.setProductId(item.getProductId());
            m.setBatchId(s.getBatch().getId());
            m.setFromSectionId(s.getSectionId());
            m.setFromSectionCode(s.getSectionCode());
            m.setQuantity(item.getQuantity());
            m.setUnitCode(s.getUnitCode());
            m.setDocumentType("WRITE_OFF");
            m.setDocumentId(w.getId());
            m.setPerformedBy(actorId);
            m.setPerformedAt(now);
            m.setComment("Списание " + w.getNumber() + ", причина " + w.getReason()
                    + (w.getComment() == null ? "" : ": " + w.getComment()));
            movementRepository.save(m);
        }
        w.setStatus(WriteOff.Status.CONFIRMED);
        w.setConfirmedBy(actorId);
        w.setConfirmedAt(now);
        log.info("AUDIT: подтверждено списание {} ({}), позиций {}, инициатор {}",
                w.getNumber(), w.getReason(), w.getItems().size(), actorId);
        return OperationMapper.toResponse(w);
    }

    @Transactional
    public WriteOffResponse cancel(UUID id, String reason, UUID actorId) {
        WriteOff w = writeOffRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("WRITE_OFF_NOT_FOUND", "Списание не найдено"));
        if (w.getStatus() != WriteOff.Status.DRAFT) {
            throw ApiException.conflict("INVALID_STATUS", "Отменить можно только черновик");
        }
        w.setStatus(WriteOff.Status.CANCELLED);
        w.setCancelledBy(actorId);
        w.setCancelReason(reason.trim());
        log.info("AUDIT: отменено списание {}, причина: {}, инициатор {}", w.getNumber(), reason, actorId);
        return OperationMapper.toResponse(w);
    }

    @Transactional(readOnly = true)
    public WriteOffResponse get(UUID id) {
        return OperationMapper.toResponse(writeOffRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("WRITE_OFF_NOT_FOUND", "Списание не найдено")));
    }

    @Transactional(readOnly = true)
    public PageResponse<WriteOffResponse> search(WriteOff.Status status, WriteOff.Reason reason, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Specification<WriteOff> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (reason != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("reason"), reason));
        return PageResponse.from(writeOffRepository.findAll(spec, pageable).map(OperationMapper::toResponse));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
