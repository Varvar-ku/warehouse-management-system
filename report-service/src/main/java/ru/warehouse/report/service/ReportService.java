package ru.warehouse.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.report.client.AuthDirectoryClient;
import ru.warehouse.report.dto.Named;
import ru.warehouse.report.dto.ReportDtos.IssueRow;
import ru.warehouse.report.dto.ReportDtos.MovementRow;
import ru.warehouse.report.dto.ReportDtos.ReceiptRow;
import ru.warehouse.report.dto.ReportDtos.WriteOffRow;
import ru.warehouse.report.dto.ReportPage;
import ru.warehouse.report.dto.ReportQuery;
import ru.warehouse.report.repository.MovementFactRepository;
import ru.warehouse.report.repository.MovementFactRepository.Filter;
import ru.warehouse.report.repository.MovementFactRepository.Slice;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 500;
    private static final Set<String> TYPES = Set.of("RECEIPT", "ISSUE", "WRITE_OFF", "TRANSFER", "ADJUSTMENT");

    // Колонки: константы, пользовательский ввод в SQL не попадает
    private static final String MOVEMENT_COLUMNS = """
            performed_at, movement_type, product_sku, product_name, batch_number, quantity, unit_code,
            from_section_code, to_section_code, document_type, document_number, performed_by""";
    private static final String RECEIPT_COLUMNS = """
            performed_at, document_number,
            context->>'supplierName' AS supplier_name,
            context->>'externalDocumentNumber' AS external_document_number,
            product_sku, product_name, batch_number, quantity, unit_code,
            context->>'recommendedSectionCode' AS recommended_section,
            to_section_code AS placed_section, performed_by""";
    private static final String ISSUE_COLUMNS = """
            performed_at, document_number,
            context->>'requestId' AS request_id, context->>'issuedTo' AS issued_to,
            product_sku, product_name, batch_number, quantity, unit_code,
            from_section_code AS section, performed_by""";
    private static final String WRITE_OFF_COLUMNS = """
            performed_at, document_number, context->>'reason' AS reason,
            product_sku, product_name, batch_number, from_section_code AS section, quantity, unit_code,
            context->>'createdBy' AS created_by, context->>'comment' AS comment""";

    private final MovementFactRepository repository;
    private final AuthDirectoryClient authDirectory;
    private final ZoneId zone;

    public ReportService(MovementFactRepository repository,
                         AuthDirectoryClient authDirectory,
                         @Value("${app.report.zone:Europe/Moscow}") String zone) {
        this.repository = repository;
        this.authDirectory = authDirectory;
        this.zone = ZoneId.of(zone);
    }

    public ReportPage<MovementRow> movements(ReportQuery q) {
        String type = null;
        if (q.type() != null && !q.type().isBlank()) {
            type = q.type().trim().toUpperCase();
            if (!TYPES.contains(type)) {
                throw ApiException.validation("Неизвестный тип операции: " + q.type());
            }
        }
        return run(null, type, q, MOVEMENT_COLUMNS, (rs, i) -> new MovementRow(
                rs.getObject("performed_at", OffsetDateTime.class), rs.getString("movement_type"),
                rs.getString("product_sku"), rs.getString("product_name"), rs.getString("batch_number"),
                rs.getBigDecimal("quantity"), rs.getString("unit_code"),
                rs.getString("from_section_code"), rs.getString("to_section_code"),
                rs.getString("document_type"), rs.getString("document_number"),
                rs.getObject("performed_by", UUID.class), null));
    }

    public ReportPage<ReceiptRow> receipts(ReportQuery q) {
        return run("RECEIPT", null, q, RECEIPT_COLUMNS, (rs, i) -> new ReceiptRow(
                rs.getObject("performed_at", OffsetDateTime.class), rs.getString("document_number"),
                rs.getString("supplier_name"), rs.getString("external_document_number"),
                rs.getString("product_sku"), rs.getString("product_name"), rs.getString("batch_number"),
                rs.getBigDecimal("quantity"), rs.getString("unit_code"),
                rs.getString("recommended_section"), rs.getString("placed_section"),
                rs.getObject("performed_by", UUID.class), null));
    }

    public ReportPage<IssueRow> issues(ReportQuery q) {
        return run("ISSUE", null, q, ISSUE_COLUMNS, (rs, i) -> new IssueRow(
                rs.getObject("performed_at", OffsetDateTime.class), rs.getString("document_number"),
                uuid(rs.getString("request_id")), uuid(rs.getString("issued_to")), null,
                rs.getString("product_sku"), rs.getString("product_name"), rs.getString("batch_number"),
                rs.getBigDecimal("quantity"), rs.getString("unit_code"),
                rs.getString("section"), rs.getObject("performed_by", UUID.class), null));
    }

    public ReportPage<WriteOffRow> writeOffs(ReportQuery q) {
        return run("WRITE_OFF", null, q, WRITE_OFF_COLUMNS, (rs, i) -> new WriteOffRow(
                rs.getObject("performed_at", OffsetDateTime.class), rs.getString("document_number"),
                rs.getString("reason"),
                rs.getString("product_sku"), rs.getString("product_name"), rs.getString("batch_number"),
                rs.getString("section"), rs.getBigDecimal("quantity"), rs.getString("unit_code"),
                uuid(rs.getString("created_by")), null, rs.getString("comment")));
    }

    private <T extends Named<T>> ReportPage<T> run(String fixedType, String typeFilter, ReportQuery q,
                                                   String columns, RowMapper<T> mapper) {
        if (q.from() != null && q.to() != null && q.from().isAfter(q.to())) {
            throw ApiException.validation("Дата «с» позже даты «по»");
        }
        int page = q.page() == null ? 0 : Math.max(q.page(), 0);
        int size = q.size() == null ? DEFAULT_SIZE : Math.min(Math.max(q.size(), 1), MAX_SIZE);
        Filter filter = new Filter(
                q.from() == null ? null : q.from().atStartOfDay(zone).toInstant(),
                q.to() == null ? null : q.to().plusDays(1).atStartOfDay(zone).toInstant(),
                q.warehouseId(), q.productId(), q.sku(), q.batchNumber(), q.documentNumber(), q.performedBy());
        Slice<T> slice = repository.find(fixedType, typeFilter, filter, columns, mapper, page, size);

        List<T> items = slice.items();
        Map<UUID, String> names = resolveNames(items);
        if (!names.isEmpty()) {
            items = items.stream().map(r -> r.withNames(names)).toList();
        }
        int totalPages = (int) ((slice.total() + size - 1) / size);
        return new ReportPage<>(items, page, size, slice.total(), totalPages);
    }

    /** Имена людей одним запросом на страницу. Если справочник недоступен, отчёт всё равно отдаётся (без имён). */
    private <T extends Named<T>> Map<UUID, String> resolveNames(List<T> rows) {
        List<UUID> ids = rows.stream().flatMap(r -> r.peopleIds().stream()).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<UUID, String> names = new HashMap<>();
            authDirectory.users(ids).forEach((id, u) ->
                    names.put(id, u.fullName() != null && !u.fullName().isBlank() ? u.fullName() : u.login()));
            return names;
        } catch (ApiException e) {
            log.warn("User directory unavailable, report returned without names: {}", e.getMessage());
            return Map.of();
        }
    }

    private static UUID uuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
