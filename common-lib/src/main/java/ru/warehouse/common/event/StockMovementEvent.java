package ru.warehouse.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshot of one stock movement (RECEIPT / ISSUE / WRITE_OFF ...), schema version 1.
 * Published by inventory-service in the same transaction as the movement, consumed by report-service.
 * Product, batch, section codes and document number are copied so a consumer never has to call back.
 * {@code context} carries document-specific fields (supplier, requestId, reason ...).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockMovementEvent(
        UUID eventId,
        int schemaVersion,
        UUID movementId,
        String movementType,
        UUID warehouseId,
        UUID productId,
        String productSku,
        String productName,
        String unitCode,
        UUID batchId,
        String batchNumber,
        Instant expiryAt,
        UUID fromSectionId,
        String fromSectionCode,
        UUID toSectionId,
        String toSectionCode,
        BigDecimal quantity,
        String documentType,
        UUID documentId,
        String documentNumber,
        UUID performedBy,
        Instant performedAt,
        String comment,
        String traceId,
        Map<String, Object> context) {

    public static final int CURRENT_SCHEMA = 1;

    public StockMovementEvent withTraceId(String newTraceId) {
        return new StockMovementEvent(eventId, schemaVersion, movementId, movementType, warehouseId, productId,
                productSku, productName, unitCode, batchId, batchNumber, expiryAt, fromSectionId, fromSectionCode,
                toSectionId, toSectionCode, quantity, documentType, documentId, documentNumber, performedBy,
                performedAt, comment, newTraceId, context);
    }
}
