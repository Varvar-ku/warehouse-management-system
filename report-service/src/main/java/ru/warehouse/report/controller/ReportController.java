package ru.warehouse.report.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.warehouse.report.dto.ReportDtos.IssueRow;
import ru.warehouse.report.dto.ReportDtos.MovementRow;
import ru.warehouse.report.dto.ReportDtos.ReceiptRow;
import ru.warehouse.report.dto.ReportDtos.WriteOffRow;
import ru.warehouse.report.dto.ReportPage;
import ru.warehouse.report.dto.ReportQuery;
import ru.warehouse.report.service.ReportService;

/** Отчёты (разд. 17 ТЗ). Доступны кладовщику и администратору. */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final String ROLES = "hasAnyRole('STOREKEEPER','ADMIN')";

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/stock-movements")
    @PreAuthorize(ROLES)
    public ReportPage<MovementRow> movements(@ModelAttribute ReportQuery q) {
        return service.movements(q);
    }

    @GetMapping("/receipts")
    @PreAuthorize(ROLES)
    public ReportPage<ReceiptRow> receipts(@ModelAttribute ReportQuery q) {
        return service.receipts(q);
    }

    @GetMapping("/issues")
    @PreAuthorize(ROLES)
    public ReportPage<IssueRow> issues(@ModelAttribute ReportQuery q) {
        return service.issues(q);
    }

    @GetMapping("/write-offs")
    @PreAuthorize(ROLES)
    public ReportPage<WriteOffRow> writeOffs(@ModelAttribute ReportQuery q) {
        return service.writeOffs(q);
    }
}
