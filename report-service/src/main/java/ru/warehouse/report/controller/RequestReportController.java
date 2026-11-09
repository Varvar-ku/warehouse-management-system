package ru.warehouse.report.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.warehouse.report.dto.DiscrepancyQuery;
import ru.warehouse.report.dto.ReportPage;
import ru.warehouse.report.dto.RequestQuery;
import ru.warehouse.report.dto.RequestReportDtos.DiscrepancyRow;
import ru.warehouse.report.dto.RequestReportDtos.RequestRow;
import ru.warehouse.report.service.RequestReportService;

/** Отчёты «Заявки» и «Расхождения» (разд. 17 ТЗ). Кладовщик и администратор. */
@RestController
@RequestMapping("/api/v1/reports")
public class RequestReportController {

    private static final String ROLES = "hasAnyRole('STOREKEEPER','ADMIN')";

    private final RequestReportService service;

    public RequestReportController(RequestReportService service) {
        this.service = service;
    }

    @GetMapping("/requests")
    @PreAuthorize(ROLES)
    public ReportPage<RequestRow> requests(@ModelAttribute RequestQuery q) {
        return service.requests(q);
    }

    @GetMapping("/discrepancies")
    @PreAuthorize(ROLES)
    public ReportPage<DiscrepancyRow> discrepancies(@ModelAttribute DiscrepancyQuery q) {
        return service.discrepancies(q);
    }
}
