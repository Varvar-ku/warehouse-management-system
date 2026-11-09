package ru.warehouse.report.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RequestReportDtos {

    private RequestReportDtos() {
    }

    private static List<UUID> ids(UUID... ids) {
        return Arrays.stream(ids).filter(Objects::nonNull).toList();
    }

    /** Отчёт «Заявки»: номер, пользователь, статус, позиции, запрошено, подтверждено, выдано, даты. */
    public record RequestRow(
            String number, UUID requesterId, String requesterName, UUID warehouseId, String status,
            Integer itemsCount,
            BigDecimal requestedQuantity, BigDecimal approvedQuantity,
            BigDecimal reservedQuantity, BigDecimal issuedQuantity,
            OffsetDateTime createdAt, OffsetDateTime closedAt, String issueNumber) implements Named<RequestRow> {

        @Override
        public List<UUID> peopleIds() {
            return ids(requesterId);
        }

        @Override
        public RequestRow withNames(Map<UUID, String> n) {
            return new RequestRow(number, requesterId, n.get(requesterId), warehouseId, status, itemsCount,
                    requestedQuantity, approvedQuantity, reservedQuantity, issuedQuantity, createdAt, closedAt,
                    issueNumber);
        }
    }

    /**
     * Отчёт «Расхождения». source REQUEST: требовалось (запрошено) и фактически (выдано);
     * source RECEIPT: по плану размещения и фактически размещено. deviation = фактически минус требовалось.
     */
    public record DiscrepancyRow(
            OffsetDateTime occurredAt, String source, String documentNumber, Integer lineNo,
            String productSku, String productName, String unitCode,
            BigDecimal requiredQuantity, BigDecimal actualQuantity, BigDecimal deviation, String reason,
            UUID responsibleId, String responsibleName,
            String plannedSection, String actualSection, String issueNumber,
            String resolution, String resolutionComment, OffsetDateTime resolvedAt,
            UUID resolvedById, String resolvedByName) implements Named<DiscrepancyRow> {

        @Override
        public List<UUID> peopleIds() {
            return ids(responsibleId, resolvedById);
        }

        @Override
        public DiscrepancyRow withNames(Map<UUID, String> n) {
            return new DiscrepancyRow(occurredAt, source, documentNumber, lineNo, productSku, productName, unitCode,
                    requiredQuantity, actualQuantity, deviation, reason, responsibleId, n.get(responsibleId),
                    plannedSection, actualSection, issueNumber, resolution, resolutionComment, resolvedAt,
                    resolvedById, n.get(resolvedById));
        }
    }
}
