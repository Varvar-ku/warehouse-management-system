package ru.warehouse.report.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.warehouse.report.dto.ReportPage;
import ru.warehouse.report.dto.StockQuery;
import ru.warehouse.report.dto.StockReportDtos.StockRow;
import ru.warehouse.report.service.StockReportService;

/** Отчёт «Остатки» (разд. 17 ТЗ). Кладовщик и администратор. */
@RestController
@RequestMapping("/api/v1/reports")
public class StockReportController {

    private static final String ROLES = "hasAnyRole('STOREKEEPER','ADMIN')";

    private final StockReportService service;

    public StockReportController(StockReportService service) {
        this.service = service;
    }

    @GetMapping("/stock")
    @PreAuthorize(ROLES)
    public ReportPage<StockRow> stock(@ModelAttribute StockQuery q) {
        return service.stock(q);
    }
}
