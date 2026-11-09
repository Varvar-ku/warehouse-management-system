package ru.warehouse.report.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Строки отчётов. Рядом с идентификатором человека стоит его имя (null, если справочник недоступен). */
public final class ReportDtos {

    private ReportDtos() {
    }

    private static List<UUID> ids(UUID... ids) {
        return Arrays.stream(ids).filter(Objects::nonNull).toList();
    }

    /** Отчёт «Движение детали». */
    public record MovementRow(
            OffsetDateTime performedAt, String movementType,
            String productSku, String productName, String batchNumber,
            BigDecimal quantity, String unitCode,
            String fromSection, String toSection,
            String documentType, String documentNumber,
            UUID performedBy, String performedByName) implements Named<MovementRow> {

        @Override
        public List<UUID> peopleIds() {
            return ids(performedBy);
        }

        @Override
        public MovementRow withNames(Map<UUID, String> n) {
            return new MovementRow(performedAt, movementType, productSku, productName, batchNumber, quantity,
                    unitCode, fromSection, toSection, documentType, documentNumber, performedBy, n.get(performedBy));
        }
    }

    /** Отчёт «Поступления». */
    public record ReceiptRow(
            OffsetDateTime performedAt, String documentNumber,
            String supplierName, String externalDocumentNumber,
            String productSku, String productName, String batchNumber,
            BigDecimal quantity, String unitCode,
            String recommendedSection, String placedSection,
            UUID performedBy, String performedByName) implements Named<ReceiptRow> {

        @Override
        public List<UUID> peopleIds() {
            return ids(performedBy);
        }

        @Override
        public ReceiptRow withNames(Map<UUID, String> n) {
            return new ReceiptRow(performedAt, documentNumber, supplierName, externalDocumentNumber, productSku,
                    productName, batchNumber, quantity, unitCode, recommendedSection, placedSection,
                    performedBy, n.get(performedBy));
        }
    }

    /** Отчёт «Выдачи». */
    public record IssueRow(
            OffsetDateTime performedAt, String documentNumber,
            UUID requestId, UUID issuedTo, String issuedToName,
            String productSku, String productName, String batchNumber,
            BigDecimal quantity, String unitCode,
            String section, UUID performedBy, String performedByName) implements Named<IssueRow> {

        @Override
        public List<UUID> peopleIds() {
            return ids(issuedTo, performedBy);
        }

        @Override
        public IssueRow withNames(Map<UUID, String> n) {
            return new IssueRow(performedAt, documentNumber, requestId, issuedTo, n.get(issuedTo), productSku,
                    productName, batchNumber, quantity, unitCode, section, performedBy, n.get(performedBy));
        }
    }

    /** Отчёт «Списания». */
    public record WriteOffRow(
            OffsetDateTime performedAt, String documentNumber, String reason,
            String productSku, String productName, String batchNumber,
            String section, BigDecimal quantity, String unitCode,
            UUID createdBy, String createdByName, String comment) implements Named<WriteOffRow> {

        @Override
        public List<UUID> peopleIds() {
            return ids(createdBy);
        }

        @Override
        public WriteOffRow withNames(Map<UUID, String> n) {
            return new WriteOffRow(performedAt, documentNumber, reason, productSku, productName, batchNumber,
                    section, quantity, unitCode, createdBy, n.get(createdBy), comment);
        }
    }
}
