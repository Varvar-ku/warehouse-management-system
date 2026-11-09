package ru.warehouse.report.dto;

import java.util.List;

/** Страница отчёта; форма совпадает с PageResponse из common-lib. */
public record ReportPage<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
