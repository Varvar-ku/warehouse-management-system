// ReservationService.java
package ru.warehouse.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.inventory.dto.OperationDtos.*;
import ru.warehouse.inventory.entity.Reservation;
import ru.warehouse.inventory.entity.Stock;
import ru.warehouse.inventory.repository.ReservationRepository;
import ru.warehouse.inventory.repository.StockRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Резервирование (разд. 11 ТЗ). Всё внутри одной транзакции: строки остатков блокируются FOR UPDATE
 * в порядке возрастания id, поэтому две заявки не зарезервируют одни и те же единицы,
 * а параллельные операции не создают взаимных блокировок.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {
    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;

    /** Проверка наличия: доступно = физический остаток минус зарезервированное. Ничего не меняет. */
    @Transactional(readOnly = true)
    public List<AvailabilityItem> availability(AvailabilityRequest req) {
        Set<UUID> productIds = new HashSet<>();
        for (AvailabilityItemRequest i : req.items()) {
            if (!productIds.add(i.productId())) {
                throw ApiException.validation("Номенклатура указана дважды: " + i.productId());
            }
        }
        Map<UUID, StockRepository.ProductTotals> totals = stockRepository.totals(req.warehouseId(), productIds)
                .stream().collect(Collectors.toMap(StockRepository.ProductTotals::getProductId, t -> t));
        return req.items().stream().map(i -> {
            StockRepository.ProductTotals t = totals.get(i.productId());
            BigDecimal available = t == null ? BigDecimal.ZERO : t.getTotal().subtract(t.getReserved());
            BigDecimal shortage = i.quantity().subtract(available).max(BigDecimal.ZERO);
            return new AvailabilityItem(i.productId(), i.quantity(), available, shortage);
        }).toList();
    }

    @Transactional
    public ReserveResponse reserve(ReserveRequest req, UUID actorId) {
        Set<UUID> itemIds = new HashSet<>();
        for (ReserveItemRequest i : req.items()) {
            if (!itemIds.add(i.requestItemId())) {
                throw ApiException.validation("Позиция заявки указана дважды: " + i.requestItemId());
            }
        }
        if (reservationRepository.existsByRequestItemIdInAndStatus(itemIds, Reservation.Status.ACTIVE)) {
            throw ApiException.conflict("RESERVATION_EXISTS", "По позиции заявки уже есть активный резерв");
        }

        // Единый порядок обработки (номенклатура, позиция) снижает риск взаимных блокировок
        List<ReserveItemRequest> ordered = new ArrayList<>(req.items());
        ordered.sort((a, b) -> {
            int c = a.productId().compareTo(b.productId());
            return c != 0 ? c : a.requestItemId().compareTo(b.requestItemId());
        });

        List<ReserveItemResult> results = new ArrayList<>();
        List<String> shortages = new ArrayList<>();
        for (ReserveItemRequest item : ordered) {
            List<UUID> ids = stockRepository.findAvailableIdsOrdered(req.warehouseId(), item.productId());
            Map<UUID, Stock> locked = stockRepository.lockAll(ids); // перечитывает и блокирует: значения теперь актуальны

            BigDecimal remaining = item.quantity();
            List<Allocation> allocations = new ArrayList<>();
            boolean unitChecked = false;
            for (UUID stockId : ids) {
                if (remaining.signum() <= 0) break;
                Stock stock = locked.get(stockId);
                if (stock == null) continue;
                if (!unitChecked) {
                    QuantityRules.require(stock.getUnitCode(), stock.getBatch().isUnitAllowFractions(),
                            item.quantity(), "Количество к резервированию");
                    unitChecked = true;
                }
                BigDecimal available = stock.available();
                if (available.signum() <= 0) continue; // кто-то успел зарезервировать до блокировки
                BigDecimal take = available.min(remaining);

                stock.setReservedQuantity(stock.getReservedQuantity().add(take));
                Reservation r = new Reservation();
                r.setRequestId(req.requestId());
                r.setRequestItemId(item.requestItemId());
                r.setStock(stock);
                r.setQuantity(take);
                r.setCreatedBy(actorId);
                r = reservationRepository.save(r);
                allocations.add(new Allocation(r.getId(), stock.getId(), stock.getSectionId(), stock.getSectionCode(),
                        stock.getBatch().getId(), stock.getBatch().getBatchNumber(), take));
                remaining = remaining.subtract(take);
            }
            BigDecimal reserved = item.quantity().subtract(remaining);
            if (remaining.signum() > 0) {
                shortages.add("Позиция заявки " + item.requestItemId() + ": запрошено " + item.quantity().toPlainString()
                        + ", доступно " + reserved.toPlainString() + ", не хватает " + remaining.toPlainString());
            }
            results.add(new ReserveItemResult(item.requestItemId(), item.productId(), item.quantity(), reserved,
                    remaining, allocations));
        }
        if (!req.allowPartial() && !shortages.isEmpty()) {
            boolean allowPartial = Boolean.TRUE.equals(req.allowPartial());
            if (!allowPartial && !shortages.isEmpty()) {
                // исключение откатывает всю транзакцию: частично созданные резервы не остаются
                throw ApiException.conflict("INSUFFICIENT_STOCK", "Недостаточно свободного остатка", shortages);
            }
        }
        log.info("AUDIT: резерв по заявке {}: позиций {}, полностью {}, инициатор {}",
                req.requestId(), results.size(), shortages.isEmpty(), actorId);
        return new ReserveResponse(req.requestId(), shortages.isEmpty(), results);
    }

    /** Снятие резерва (отмена или отклонение заявки). Повторный вызов безопасен: активных резервов уже нет. */
    @Transactional
    public ReleaseResponse release(ReleaseRequest req, UUID actorId) {
        List<Reservation> active = req.requestItemIds() == null || req.requestItemIds().isEmpty()
                ? reservationRepository.lockByRequestAndStatus(req.requestId(), Reservation.Status.ACTIVE)
                : reservationRepository.lockByRequestItemsAndStatus(req.requestId(), req.requestItemIds(),
                Reservation.Status.ACTIVE);
        Map<UUID, Stock> stocks = stockRepository.lockAll(
                active.stream().map(r -> r.getStock().getId()).collect(Collectors.toSet()));
        OffsetDateTime now = OffsetDateTime.now();
        BigDecimal total = BigDecimal.ZERO;
        for (Reservation r : active) {
            Stock stock = stocks.get(r.getStock().getId());
            stock.setReservedQuantity(stock.getReservedQuantity().subtract(r.getQuantity()));
            r.setReleasedQuantity(r.getQuantity());
            r.setStatus(Reservation.Status.RELEASED);
            r.setClosedAt(now);
            r.setClosedBy(actorId);
            total = total.add(r.getQuantity());
        }
        log.info("AUDIT: снят резерв по заявке {}: записей {}, количество {}, инициатор {}",
                req.requestId(), active.size(), total, actorId);
        return new ReleaseResponse(req.requestId(), active.size(), total);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(UUID requestId) {
        return reservationRepository.findByRequestIdOrderByCreatedAtAscIdAsc(requestId).stream()
                .map(OperationMapper::toResponse).toList();
    }
}
