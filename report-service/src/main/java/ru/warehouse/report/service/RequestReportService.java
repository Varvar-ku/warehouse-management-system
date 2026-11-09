package ru.warehouse.report.service;

import org.springframework.stereotype.Service;
import ru.warehouse.report.dto.DiscrepancyQuery;
import ru.warehouse.report.dto.ReportPage;
import ru.warehouse.report.dto.RequestQuery;
import ru.warehouse.report.dto.RequestReportDtos.DiscrepancyRow;
import ru.warehouse.report.dto.RequestReportDtos.RequestRow;
import ru.warehouse.report.repository.DiscrepancyRepository;
import ru.warehouse.report.repository.RequestFactRepository;
import ru.warehouse.common.exception.ApiException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Отчёты «Заявки» и «Расхождения». */
@Service
public class RequestReportService {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 500;

    private final RequestFactRepository requests;
    private final DiscrepancyRepository discrepancies;
    private final ReportPeriods periods;
    private final NameResolver names;

    public RequestReportService(RequestFactRepository requests, DiscrepancyRepository discrepancies,
                                ReportPeriods periods, NameResolver names) {
        this.requests = requests;
        this.discrepancies = discrepancies;
        this.periods = periods;
        this.names = names;
    }

    public ReportPage<RequestRow> requests(RequestQuery q) {
        periods.validate(q.from(), q.to());
        int page = page(q.page());
        int size = size(q.size());
        String status = q.status() == null || q.status().isBlank() ? null : q.status().trim().toUpperCase();
        var filter = new RequestFactRepository.Filter(periods.start(q.from()), periods.endExclusive(q.to()),
                status, q.requesterId(), q.number(), q.warehouseId());
        var slice = requests.find(filter, page, size, (rs, i) -> new RequestRow(
                rs.getString("number"), rs.getObject("requester_id", UUID.class), null,
                rs.getObject("warehouse_id", UUID.class), rs.getString("status"),
                (Integer) rs.getObject("items_count"),
                rs.getBigDecimal("requested_quantity"), rs.getBigDecimal("approved_quantity"),
                rs.getBigDecimal("reserved_quantity"), rs.getBigDecimal("issued_quantity"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("closed_at", OffsetDateTime.class),
                rs.getString("issue_number")));
        return result(names.apply(slice.items()), page, size, slice.total());
    }

    public ReportPage<DiscrepancyRow> discrepancies(DiscrepancyQuery q) {
        periods.validate(q.from(), q.to());
        int page = page(q.page());
        int size = size(q.size());
        String source = null;
        if (q.source() != null && !q.source().isBlank()) {
            source = q.source().trim().toUpperCase();
            if (!source.equals("REQUEST") && !source.equals("RECEIPT")) {
                throw ApiException.validation("source: REQUEST или RECEIPT");
            }
        }
        var filter = new DiscrepancyRepository.Filter(periods.start(q.from()), periods.endExclusive(q.to()), source,
                q.documentNumber(), q.sku(), q.responsibleId(), Boolean.TRUE.equals(q.unresolvedOnly()));
        var slice = discrepancies.find(filter, page, size, (rs, i) -> new DiscrepancyRow(
                rs.getObject("occurred_at", OffsetDateTime.class), rs.getString("source"),
                rs.getString("document_number"), (Integer) rs.getObject("line_no"),
                rs.getString("product_sku"), rs.getString("product_name"), rs.getString("unit_code"),
                rs.getBigDecimal("required_quantity"), rs.getBigDecimal("actual_quantity"),
                rs.getBigDecimal("deviation"), rs.getString("reason"),
                rs.getObject("responsible_id", UUID.class), null,
                rs.getString("planned_section"), rs.getString("actual_section"), rs.getString("issue_number"),
                rs.getString("resolution"), rs.getString("resolution_comment"),
                rs.getObject("resolved_at", OffsetDateTime.class),
                rs.getObject("resolved_by", UUID.class), null));
        return result(names.apply(slice.items()), page, size, slice.total());
    }

    private static <T> ReportPage<T> result(List<T> items, int page, int size, long total) {
        return new ReportPage<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    private static int page(Integer p) {
        return p == null ? 0 : Math.max(p, 0);
    }

    private static int size(Integer s) {
        return s == null ? DEFAULT_SIZE : Math.min(Math.max(s, 1), MAX_SIZE);
    }
}
