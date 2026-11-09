package ru.warehouse.report.service;

import org.springframework.stereotype.Service;
import ru.warehouse.report.dto.ReportPage;
import ru.warehouse.report.dto.StockQuery;
import ru.warehouse.report.dto.StockReportDtos.StockRow;
import ru.warehouse.report.repository.StockBalanceRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Отчёт «Остатки» (разд. 17 ТЗ): физический остаток по секциям и партиям на дату.
 * Считается из movement_facts, поэтому можно получить срез на любую прошлую дату.
 * Резерв и доступное количество здесь НЕ показываются: события движений резервы не содержат,
 * а reservation.created / released несут только итоги по заявке. Текущий резерв по строкам
 * остатка отдаёт inventory-service (GET /api/v1/stock).
 */
@Service
public class StockReportService {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 500;

    private final StockBalanceRepository repository;
    private final ReportPeriods periods;

    public StockReportService(StockBalanceRepository repository, ReportPeriods periods) {
        this.repository = repository;
        this.periods = periods;
    }

    public ReportPage<StockRow> stock(StockQuery q) {
        int page = q.page() == null ? 0 : Math.max(q.page(), 0);
        int size = q.size() == null ? DEFAULT_SIZE : Math.min(Math.max(q.size(), 1), MAX_SIZE);
        var filter = new StockBalanceRepository.Filter(periods.endExclusive(q.asOf()), q.warehouseId(),
                q.sectionId(), q.productId(), q.sku(), q.batchNumber(), Boolean.TRUE.equals(q.includeZero()));
        var slice = repository.find(filter, (rs, i) -> new StockRow(
                rs.getObject("warehouse_id", UUID.class), rs.getObject("section_id", UUID.class),
                rs.getString("section_code"),
                rs.getObject("product_id", UUID.class), rs.getString("product_sku"), rs.getString("product_name"),
                rs.getObject("batch_id", UUID.class), rs.getString("batch_number"),
                rs.getObject("expiry_at", OffsetDateTime.class),
                rs.getString("unit_code"), rs.getBigDecimal("quantity")), page, size);
        long total = slice.total();
        return new ReportPage<>(slice.items(), page, size, total, (int) ((total + size - 1) / size));
    }
}
