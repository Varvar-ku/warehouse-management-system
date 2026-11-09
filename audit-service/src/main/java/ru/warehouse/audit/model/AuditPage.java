package ru.warehouse.audit.model;

import java.util.List;

public record AuditPage(List<AuditEventView> items, int page, int size, long totalItems, int totalPages) {
}
