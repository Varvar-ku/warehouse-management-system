package ru.warehouse.inventory.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.inventory.dto.OperationDtos.*;
import ru.warehouse.inventory.service.IssueService;
import ru.warehouse.inventory.service.ReservationService;
import ru.warehouse.inventory.service.ShortageService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B1: недобор при сборе. Резерв 10, выдано 6, недобор 4: остаток уменьшается на выданное, строка уходит «на проверку»,
 * недостача регистрируется и не возвращается в оборот; решение «найдено» снимает блокировку.
 */
class ShortageFlowIntegrationTest extends IntegrationTestBase {
    @Autowired ReservationService reservations;
    @Autowired IssueService issues;
    @Autowired ShortageService shortages;

    private final UUID storekeeper = UUID.randomUUID();

    @Test
    void shortageBlocksStock_untilFound() {
        UUID warehouse = UUID.randomUUID(), product = UUID.randomUUID(), requestId = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        UUID stock = StockFixtures.stock(jdbc, warehouse, product, StockFixtures.Spec.plain("A-1", "S", "10"));

        ReserveResponse reserved = reservations.reserve(new ReserveRequest(requestId, warehouse, false,
                List.of(new ReserveItemRequest(item, product, new BigDecimal("10")))), storekeeper);
        UUID reservationId = reserved.items().get(0).allocations().get(0).reservationId();

        IssueResponse draft = issues.create(new CreateIssueRequest(requestId, warehouse, UUID.randomUUID(), null,
                List.of(new IssueItemRequest(item, product, new BigDecimal("6"))),
                List.of(new ShortageMark(reservationId, new BigDecimal("4")))), storekeeper);
        issues.confirm(draft.id(), storekeeper);

        BigDecimal quantity = jdbc.queryForObject("SELECT quantity FROM stock WHERE id = ?", BigDecimal.class, stock);
        BigDecimal reservedQty = jdbc.queryForObject("SELECT reserved_quantity FROM stock WHERE id = ?", BigDecimal.class, stock);
        Boolean onHold = jdbc.queryForObject("SELECT on_hold FROM stock WHERE id = ?", Boolean.class, stock);
        assertThat(quantity).isEqualByComparingTo("4");
        assertThat(reservedQty).isEqualByComparingTo("0");
        assertThat(onHold).isTrue();

        UUID shortageId = jdbc.queryForObject("SELECT id FROM stock_shortages WHERE request_id = ?", UUID.class, requestId);
        assertThat(jdbc.queryForObject("SELECT quantity FROM stock_shortages WHERE id = ?", BigDecimal.class, shortageId))
                .isEqualByComparingTo("4");

        // недостача не возвращается в оборот: ни в наличие, ни в резерв
        assertThat(reservations.availability(new AvailabilityRequest(warehouse,
                List.of(new AvailabilityItemRequest(product, BigDecimal.ONE)))).get(0).available()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> reservations.reserve(new ReserveRequest(UUID.randomUUID(), warehouse, false,
                List.of(new ReserveItemRequest(UUID.randomUUID(), product, BigDecimal.ONE))), storekeeper))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INSUFFICIENT_STOCK");

        // решение «найдено»: блокировка снимается, остаток снова доступен
        shortages.found(shortageId, "пересчитали, товар на месте", storekeeper);
        assertThat(jdbc.queryForObject("SELECT on_hold FROM stock WHERE id = ?", Boolean.class, stock)).isFalse();
        assertThat(reservations.availability(new AvailabilityRequest(warehouse,
                List.of(new AvailabilityItemRequest(product, BigDecimal.ONE)))).get(0).available()).isEqualByComparingTo("4");
    }
}
