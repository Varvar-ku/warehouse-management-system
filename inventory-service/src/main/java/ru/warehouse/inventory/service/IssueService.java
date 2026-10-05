// IssueService.java
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
import ru.warehouse.inventory.dto.OperationDtos.*;
import ru.warehouse.inventory.entity.*;
import ru.warehouse.inventory.repository.IssueRepository;
import ru.warehouse.inventory.repository.ReservationRepository;
import ru.warehouse.inventory.repository.StockMovementRepository;
import ru.warehouse.inventory.repository.StockRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Документы передачи (разд. 5.5 и 13 ТЗ). */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueService {
    private final IssueRepository issueRepository;
    private final ReservationRepository reservationRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;

    /**
     * Черновик документа: в нём только фактически выдаваемые количества. Каждая позиция раскладывается
     * по активным резервам своей позиции заявки (по порядку создания). Остаток пока не меняется.
     */
    @Transactional
    public IssueResponse create(CreateIssueRequest req, UUID actorId) {
        if (issueRepository.existsByRequestIdAndStatus(req.requestId(), Issue.Status.DRAFT)) {
            throw ApiException.conflict("DRAFT_EXISTS", "По заявке уже есть черновик документа передачи");
        }
        Issue issue = new Issue();
        issue.setNumber(String.format("IS-%d-%06d", OffsetDateTime.now().getYear(), issueRepository.nextNumber()));
        issue.setWarehouseId(req.warehouseId());
        issue.setRequestId(req.requestId());
        issue.setIssuedTo(req.issuedTo());
        issue.setCreatedBy(actorId);
        issue.setComment(blankToNull(req.comment()));

        Set<UUID> itemIds = new HashSet<>();
        for (IssueItemRequest ir : req.items()) {
            if (!itemIds.add(ir.requestItemId())) {
                throw ApiException.validation("Позиция заявки указана дважды: " + ir.requestItemId());
            }
            List<Reservation> reservations = reservationRepository
                    .findByRequestItemIdAndStatusOrderByCreatedAtAscIdAsc(ir.requestItemId(), Reservation.Status.ACTIVE);
            if (reservations.isEmpty()) {
                throw ApiException.conflict("NO_RESERVATION", "По позиции заявки нет активного резерва: " + ir.requestItemId());
            }
            BigDecimal reserved = BigDecimal.ZERO;
            for (Reservation r : reservations) {
                Stock s = r.getStock();
                if (!r.getRequestId().equals(req.requestId())) {
                    throw ApiException.validation("Позиция заявки " + ir.requestItemId() + " относится к другой заявке");
                }
                if (!s.getProductId().equals(ir.productId()) || !s.getWarehouseId().equals(req.warehouseId())) {
                    throw ApiException.validation("Номенклатура или склад не соответствуют резерву по позиции " + ir.requestItemId());
                }
                reserved = reserved.add(r.getQuantity());
            }
            if (reserved.compareTo(ir.quantity()) < 0) {
                throw ApiException.conflict("INSUFFICIENT_RESERVATION", "По позиции " + ir.requestItemId()
                        + " в резерве " + reserved.toPlainString() + ", к выдаче " + ir.quantity().toPlainString());
            }
            Stock first = reservations.get(0).getStock();
            QuantityRules.require(first.getUnitCode(), first.getBatch().isUnitAllowFractions(),
                    ir.quantity(), "Количество к выдаче");

            BigDecimal remaining = ir.quantity();
            for (Reservation r : reservations) {
                if (remaining.signum() <= 0) break;
                BigDecimal take = r.getQuantity().min(remaining);
                IssueItem item = new IssueItem();
                item.setIssue(issue);
                item.setRequestItemId(ir.requestItemId());
                item.setProductId(ir.productId());
                item.setStock(r.getStock());
                item.setReservation(r);
                item.setQuantity(take);
                issue.getItems().add(item);
                remaining = remaining.subtract(take);
            }
        }
        issue = issueRepository.save(issue);
        log.info("AUDIT: создан документ передачи {} по заявке {}, инициатор {}", issue.getNumber(), req.requestId(), actorId);
        return OperationMapper.toResponse(issue);
    }

    /**
     * Подтверждение выдачи (разд. 13 ТЗ), одна транзакция: блокировка документа, резервов и остатков;
     * резерв → CONSUMED, физический остаток уменьшается, пишутся движения ISSUE.
     * Выдача окончательная: неиспользованная часть резервов ВСЕЙ заявки освобождается.
     */
    @Transactional
    public IssueResponse confirm(UUID id, UUID actorId) {
        Issue issue = issueRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("ISSUE_NOT_FOUND", "Документ передачи не найден"));
        if (issue.getStatus() != Issue.Status.DRAFT) {
            throw ApiException.conflict("INVALID_STATUS", "Документ в статусе " + issue.getStatus() + " подтвердить нельзя");
        }
        List<Reservation> reservations = reservationRepository
                .lockByRequestAndStatus(issue.getRequestId(), Reservation.Status.ACTIVE);
        Map<UUID, Reservation> byId = reservations.stream().collect(Collectors.toMap(Reservation::getId, r -> r));

        Map<UUID, BigDecimal> consumed = new HashMap<>();
        for (IssueItem it : issue.getItems()) {
            if (!byId.containsKey(it.getReservation().getId())) {
                throw ApiException.conflict("RESERVATION_CHANGED",
                        "Резерв, на котором основан документ, уже снят или использован: создайте документ заново");
            }
            consumed.merge(it.getReservation().getId(), it.getQuantity(), BigDecimal::add);
        }
        Set<UUID> stockIds = reservations.stream().map(r -> r.getStock().getId()).collect(Collectors.toSet());
        Map<UUID, Stock> stocks = stockRepository.lockAll(stockIds);

        OffsetDateTime now = OffsetDateTime.now();
        for (Reservation r : reservations) {
            BigDecimal used = consumed.getOrDefault(r.getId(), BigDecimal.ZERO);
            Stock stock = stocks.get(r.getStock().getId());
            stock.setQuantity(stock.getQuantity().subtract(used));
            stock.setReservedQuantity(stock.getReservedQuantity().subtract(r.getQuantity())); // и использованное, и освобождаемое
            r.setConsumedQuantity(used);
            r.setReleasedQuantity(r.getQuantity().subtract(used));
            r.setStatus(used.signum() > 0 ? Reservation.Status.CONSUMED : Reservation.Status.RELEASED);
            r.setClosedAt(now);
            r.setClosedBy(actorId);
        }
        for (IssueItem it : issue.getItems()) {
            Stock stock = stocks.get(it.getStock().getId());
            StockMovement m = new StockMovement();
            m.setMovementType(StockMovement.Type.ISSUE);
            m.setWarehouseId(issue.getWarehouseId());
            m.setProductId(it.getProductId());
            m.setBatchId(stock.getBatch().getId());
            m.setFromSectionId(stock.getSectionId());
            m.setFromSectionCode(stock.getSectionCode());
            m.setQuantity(it.getQuantity());
            m.setUnitCode(stock.getUnitCode());
            m.setDocumentType("ISSUE");
            m.setDocumentId(issue.getId());
            m.setPerformedBy(actorId);
            m.setPerformedAt(now);
            m.setComment("Выдача " + issue.getNumber() + " по заявке " + issue.getRequestId());
            movementRepository.save(m);
        }
        issue.setStatus(Issue.Status.CONFIRMED);
        issue.setIssuedBy(actorId);
        issue.setIssuedAt(now);
        log.info("AUDIT: подтверждена выдача {} по заявке {}, позиций {}, инициатор {}",
                issue.getNumber(), issue.getRequestId(), issue.getItems().size(), actorId);
        return OperationMapper.toResponse(issue);
    }

    @Transactional
    public IssueResponse cancel(UUID id, String reason, UUID actorId) {
        Issue issue = issueRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("ISSUE_NOT_FOUND", "Документ передачи не найден"));
        if (issue.getStatus() != Issue.Status.DRAFT) {
            throw ApiException.conflict("INVALID_STATUS", "Отменить можно только черновик");
        }
        issue.setStatus(Issue.Status.CANCELLED);
        issue.setCancelReason(reason.trim());
        log.info("AUDIT: отменён черновик выдачи {}, причина: {}, инициатор {}", issue.getNumber(), reason, actorId);
        return OperationMapper.toResponse(issue);
    }

    @Transactional(readOnly = true)
    public IssueResponse get(UUID id) {
        return OperationMapper.toResponse(issueRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ISSUE_NOT_FOUND", "Документ передачи не найден")));
    }

    @Transactional(readOnly = true)
    public PageResponse<IssueResponse> search(Issue.Status status, UUID requestId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Specification<Issue> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (requestId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("requestId"), requestId));
        return PageResponse.from(issueRepository.findAll(spec, pageable).map(OperationMapper::toResponse));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}