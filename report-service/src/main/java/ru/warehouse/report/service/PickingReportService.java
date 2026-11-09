package ru.warehouse.report.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.report.dto.PickingDtos.PickingEventRow;
import ru.warehouse.report.dto.PickingDtos.WorkerPerformanceRow;
import ru.warehouse.report.dto.PickingQuery;
import ru.warehouse.report.dto.ReportPage;
import ru.warehouse.report.repository.PickingEventRepository;
import ru.warehouse.report.repository.PickingEventRepository.Filter;
import ru.warehouse.report.repository.PickingEventRepository.Slice;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PickingReportService {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 500;
    private static final Set<String> ACTIONS = Set.of(
            "picking.task_created", "picking.worker_added", "picking.items_auto_assigned",
            "picking.items_assigned", "picking.worker_status_changed", "picking.worker_role_changed",
            "picking.item_picked", "picking.shortage_confirmed", "picking.worker_removed",
            "picking.task_completed", "picking.task_rejected");

    private final PickingEventRepository repository;
    private final NameResolver names;
    private final ZoneId zone;

    public PickingReportService(PickingEventRepository repository, NameResolver names,
                                @Value("${app.report.zone:Europe/Moscow}") String zone) {
        this.repository = repository;
        this.names = names;
        this.zone = ZoneId.of(zone);
    }

    /** Журнал операций комплектации: назначения, снятия, сбор, завершение. */
    public ReportPage<PickingEventRow> events(PickingQuery q) {
        Filter filter = filter(q);
        int page = q.page() == null ? 0 : Math.max(q.page(), 0);
        int size = q.size() == null ? DEFAULT_SIZE : Math.min(Math.max(q.size(), 1), MAX_SIZE);
        Slice<PickingEventRow> slice = repository.findEvents(filter, page, size, (rs, i) -> new PickingEventRow(
                rs.getObject("occurred_at", OffsetDateTime.class), rs.getString("action"),
                rs.getString("task_number"),
                rs.getObject("actor_id", UUID.class), null,
                rs.getObject("worker_id", UUID.class), null,
                uuid(rs.getString("replace_with")), null,
                rs.getString("request_number"), (Integer) rs.getObject("line_no"),
                rs.getString("section_code"), rs.getString("batch_number"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("picked_quantity"),
                (Boolean) rs.getObject("shortage"), rs.getString("note")));
        List<PickingEventRow> items = names.apply(slice.items());
        int totalPages = (int) ((slice.total() + size - 1) / size);
        return new ReportPage<>(items, page, size, slice.total(), totalPages);
    }

    /** Сводка по исполнителям за период: задания, строки, назначено и собрано, недостачи, снятия. */
    public ReportPage<WorkerPerformanceRow> workers(PickingQuery q) {
        List<WorkerPerformanceRow> rows = names.apply(repository.workerPerformance(filter(q)));
        return new ReportPage<>(rows, 0, Math.max(rows.size(), 1), rows.size(), 1);
    }

    private Filter filter(PickingQuery q) {
        if (q.from() != null && q.to() != null && q.from().isAfter(q.to())) {
            throw ApiException.validation("Дата «с» позже даты «по»");
        }
        String action = null;
        if (q.action() != null && !q.action().isBlank()) {
            action = q.action().trim();
            if (!ACTIONS.contains(action)) {
                throw ApiException.validation("Неизвестное действие: " + q.action());
            }
        }
        return new Filter(
                q.from() == null ? null : q.from().atStartOfDay(zone).toInstant(),
                q.to() == null ? null : q.to().plusDays(1).atStartOfDay(zone).toInstant(),
                q.workerId(), q.taskNumber(), action);
    }

    private static UUID uuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
