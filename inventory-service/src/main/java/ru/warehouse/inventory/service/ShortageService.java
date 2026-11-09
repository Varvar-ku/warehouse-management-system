// ShortageService.java
package ru.warehouse.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.common.event.OutboxPublisher;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.common.numbering.DocumentNumbers;
import ru.warehouse.inventory.dto.AdjustmentDtos.ShortageResponse;
import ru.warehouse.inventory.entity.Issue;
import ru.warehouse.inventory.entity.Reservation;
import ru.warehouse.inventory.entity.Stock;
import ru.warehouse.inventory.entity.StockShortage;
import ru.warehouse.inventory.repository.StockRepository;
import ru.warehouse.inventory.repository.StockShortageRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import static ru.warehouse.common.event.OutboxPublisher.details;

/**
 * Недостачи при сборе (B1). Регистрируются при подтверждении выдачи по неиспользованной части резерва:
 * строка остатка блокируется (onHold) и выпадает из резерва и проверки наличия, пока кладовщик не решит,
 * что это: «найдено» (остаток верен) или реальная недостача (документ корректировки, AdjustmentService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortageService {
    private final StockShortageRepository shortageRepository;
    private final StockRepository stockRepository;
    private final OutboxPublisher outbox;
    private final DocumentNumbers numbers;

    private static final String SERVICE = "inventory-service";

    /** Вызывается из IssueService.confirm в его транзакции; строка остатка уже заблокирована вызывающим. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockShortage register(Issue issue, Reservation reservation, Stock stock, BigDecimal quantity,
                                  UUID actorId) {
        return create(issue, reservation, stock, quantity, actorId);
    }

    /** Недостача при снятии резерва отменённой заявки (недобор при сборе): документа выдачи нет. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockShortage registerOnRelease(Reservation reservation, Stock stock, BigDecimal quantity, UUID actorId) {
        return create(null, reservation, stock, quantity, actorId);
    }

    private StockShortage create(Issue issue, Reservation reservation, Stock stock, BigDecimal quantity,
                                 UUID actorId) {
        OffsetDateTime now = OffsetDateTime.now();
        StockShortage s = new StockShortage();
        s.setNumber(numbers.next("SH"));
        s.setWarehouseId(stock.getWarehouseId());
        s.setProductId(stock.getProductId());
        s.setStock(stock);
        s.setReservationId(reservation.getId());
        s.setRequestId(reservation.getRequestId());
        s.setRequestItemId(reservation.getRequestItemId());
        if (issue != null) {
            s.setIssueId(issue.getId());
            s.setSource(StockShortage.Source.ISSUE);
        } else {
            s.setSource(StockShortage.Source.CANCELLATION);
        }
        s.setQuantity(quantity);
        s.setUnitCode(stock.getUnitCode());
        s.setReportedBy(actorId);
        s = shortageRepository.save(s);

        if (!stock.isOnHold()) {
            stock.setOnHold(true);
            stock.setHoldSince(now);
        }
        outbox.audit(SERVICE, "shortage.registered", "STOCK_SHORTAGE", s.getId(), actorId, details(
                "number", s.getNumber(),
                "source", s.getSource().name(),
                "issueNumber", issue == null ? null : issue.getNumber(),
                "requestId", reservation.getRequestId(),
                "requestItemId", reservation.getRequestItemId(),
                "stockId", stock.getId(),
                "section", stock.getSectionCode(),
                "batch", stock.getBatch().getBatchNumber(),
                "quantity", quantity.toPlainString()));
        return s;
    }

    /** Решение «найдено»: недостачи нет, остаток верен. Если других открытых недостач по строке нет, блокировка снимается. */
    @Transactional
    public ShortageResponse found(UUID id, String comment, UUID actorId) {
        StockShortage s = shortageRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("SHORTAGE_NOT_FOUND", "Недостача не найдена"));
        if (s.getStatus() != StockShortage.Status.OPEN) {
            throw ApiException.conflict("INVALID_STATUS", "Недостача уже закрыта: " + s.getResolution());
        }
        if (s.getAdjustmentId() != null) {
            throw ApiException.conflict("SHORTAGE_IN_ADJUSTMENT",
                    "Недостача включена в документ корректировки: отмените его, чтобы изменить решение");
        }
        Stock stock = stockRepository.lockAll(List.of(s.getStock().getId())).get(s.getStock().getId());
        s.setStatus(StockShortage.Status.RESOLVED);
        s.setResolution(StockShortage.Resolution.FOUND);
        s.setResolutionComment(comment.trim());
        s.setResolvedBy(actorId);
        s.setResolvedAt(OffsetDateTime.now());
        releaseHoldIfResolved(List.of(stock));
        outbox.audit(SERVICE, "shortage.found", "STOCK_SHORTAGE", s.getId(), actorId, details(
                "number", s.getNumber(),
                "stockId", stock.getId(),
                "quantity", s.getQuantity().toPlainString(),
                "comment", comment.trim()));
        return ShortageMapper.toResponse(s);
    }

    /**
     * Снимает блокировку со строк, по которым не осталось открытых недостач.
     * Вызывается после смены статусов недостач в той же транзакции; строки остатков уже заблокированы.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseHoldIfResolved(Collection<Stock> stocks) {
        shortageRepository.flush();
        for (Stock stock : stocks) {
            if (stock.isOnHold()
                    && !shortageRepository.existsByStockIdAndStatus(stock.getId(), StockShortage.Status.OPEN)) {
                stock.setOnHold(false);
                stock.setHoldSince(null);
            }
        }
    }

    @Transactional(readOnly = true)
    public ShortageResponse get(UUID id) {
        return ShortageMapper.toResponse(shortageRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("SHORTAGE_NOT_FOUND", "Недостача не найдена")));
    }

    @Transactional(readOnly = true)
    public PageResponse<ShortageResponse> search(StockShortage.Status status, UUID warehouseId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Specification<StockShortage> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (warehouseId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("warehouseId"), warehouseId));
        return PageResponse.from(shortageRepository.findAll(spec, pageable).map(ShortageMapper::toResponse));
    }
}
