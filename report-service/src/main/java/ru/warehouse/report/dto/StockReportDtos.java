package ru.warehouse.report.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Строки отчёта «Остатки». */
public final class StockReportDtos {

    private StockReportDtos() {
    }

    /** Физический остаток партии в секции на выбранную дату. Резерв в строку не входит (см. StockReportService). */
    public record StockRow(
            UUID warehouseId, UUID sectionId, String sectionCode,
            UUID productId, String productSku, String productName,
            UUID batchId, String batchNumber, OffsetDateTime expiryAt,
            String unitCode, BigDecimal quantity) {
    }
}
