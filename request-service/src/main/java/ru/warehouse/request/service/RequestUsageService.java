package ru.warehouse.request.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.warehouse.common.exception.ApiException;
import java.util.UUID;

/**
 * M4: сколько открытых заявок ссылается на номенклатуру или склад. Открытая: от SUBMITTED до READY_FOR_ISSUE
 * (черновики не считаются: при отправке номенклатура и склад проверяются заново).
 */
@Service
@RequiredArgsConstructor
public class RequestUsageService {
    private static final String CLOSED = "('DRAFT', 'ISSUED', 'CLOSED', 'REJECTED', 'CANCELLED')";

    private final JdbcTemplate jdbc;

    public long openRequests(UUID productId, UUID warehouseId) {
        if (productId == null && warehouseId == null) {
            throw ApiException.validation("Укажите productId или warehouseId");
        }
        if (productId != null) {
            Long n = jdbc.queryForObject("""
                    SELECT count(DISTINCT r.id) FROM requests r JOIN request_items i ON i.request_id = r.id
                    WHERE i.product_id = ? AND r.status NOT IN %s""".formatted(CLOSED), Long.class, productId);
            return n == null ? 0 : n;
        }
        Long n = jdbc.queryForObject("SELECT count(*) FROM requests WHERE warehouse_id = ? AND status NOT IN " + CLOSED,
                Long.class, warehouseId);
        return n == null ? 0 : n;
    }
}
