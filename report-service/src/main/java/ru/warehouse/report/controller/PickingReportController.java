package ru.warehouse.report.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.warehouse.report.dto.PickingDtos.PickingEventRow;
import ru.warehouse.report.dto.PickingDtos.WorkerPerformanceRow;
import ru.warehouse.report.dto.PickingQuery;
import ru.warehouse.report.dto.ReportPage;
import ru.warehouse.report.service.PickingReportService;

/** Отчёты по комплектации: журнал операций и сводка по исполнителям. Кладовщик и администратор. */
@RestController
@RequestMapping("/api/v1/reports/picking")
public class PickingReportController {

    private static final String ROLES = "hasAnyRole('STOREKEEPER','ADMIN')";

    private final PickingReportService service;

    public PickingReportController(PickingReportService service) {
        this.service = service;
    }

    @GetMapping("/events")
    @PreAuthorize(ROLES)
    public ReportPage<PickingEventRow> events(@ModelAttribute PickingQuery q) {
        return service.events(q);
    }

    @GetMapping("/workers")
    @PreAuthorize(ROLES)
    public ReportPage<WorkerPerformanceRow> workers(@ModelAttribute PickingQuery q) {
        return service.workers(q);
    }
}
