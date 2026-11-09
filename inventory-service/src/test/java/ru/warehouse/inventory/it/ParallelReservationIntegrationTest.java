package ru.warehouse.inventory.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Параллельное резервирование одного остатка (R15). Инварианты: резерв не превышает остаток, сумма активных резервов
 * равна `stock.reserved_quantity`, ни один запрос не заканчивается ошибкой сервера.
 */
class ParallelReservationIntegrationTest extends IntegrationTestBase {

    private record StockRow(UUID warehouse, UUID product, UUID id) {}

    private StockRow createStock(String quantity) {
        UUID warehouse = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID section = UUID.randomUUID();
        UUID receipt = UUID.randomUUID();
        UUID batch = UUID.randomUUID();
        UUID stock = UUID.randomUUID();
        jdbc.update("INSERT INTO receipts (id, number, warehouse_id, supplier_name, status, received_at, created_by) "
                + "VALUES (?, ?, ?, 'test', 'DONE', ?, ?)", receipt, "RC-T-" + receipt, warehouse,
                OffsetDateTime.now(), UUID.randomUUID());
        jdbc.update("INSERT INTO batches (id, product_id, product_sku, product_name, unit_code, receipt_id, batch_number, received_at) "
                + "VALUES (?, ?, 'SKU', 'name', 'PCS', ?, ?, ?)", batch, product, receipt, "B-" + batch, OffsetDateTime.now());
        jdbc.update("INSERT INTO stock (id, warehouse_id, product_id, section_id, section_code, batch_id, unit_code, quantity, reserved_quantity) "
                + "VALUES (?, ?, ?, ?, 'A-1', ?, 'PCS', ?, 0)", stock, warehouse, product, section, batch, new BigDecimal(quantity));
        return new StockRow(warehouse, product, stock);
    }

    private int reserve(StockRow s, String quantity, boolean allowPartial) {
        String body = """
                {"requestId":"%s","warehouseId":"%s","allowPartial":%s,
                 "items":[{"requestItemId":"%s","productId":"%s","quantity":%s}]}
                """.formatted(UUID.randomUUID(), s.warehouse(), allowPartial, UUID.randomUUID(), s.product(), quantity);
        try {
            return mvc.perform(MockMvcRequestBuilders.post("/api/v1/reservations")
                            .header("Authorization", "Bearer " + TestTokens.service(UUID.randomUUID(), "request-service"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getStatus();
        } catch (Exception ex) {
            return 500;
        }
    }

    private List<Integer> parallel(int count, Callable<Integer> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }
        ready.await();
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) statuses.add(f.get());
        pool.shutdown();
        return statuses;
    }

    private void assertInvariants(StockRow s, String expectedReserved) {
        BigDecimal quantity = jdbc.queryForObject("SELECT quantity FROM stock WHERE id = ?", BigDecimal.class, s.id());
        BigDecimal reserved = jdbc.queryForObject("SELECT reserved_quantity FROM stock WHERE id = ?", BigDecimal.class, s.id());
        BigDecimal active = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM reservations WHERE stock_id = ? AND status = 'ACTIVE'",
                BigDecimal.class, s.id());
        assertThat(reserved).as("резерв не превышает остаток").isLessThanOrEqualTo(quantity);
        assertThat(active).as("сумма активных резервов = reserved_quantity").isEqualByComparingTo(reserved);
        assertThat(reserved).isEqualByComparingTo(new BigDecimal(expectedReserved));
    }

    @Test
    void partialReservations_neverOversellAndUseTheWholeStock() throws Exception {
        StockRow s = createStock("10");
        List<Integer> statuses = parallel(8, () -> reserve(s, "3", true)); // спрос 24 на остаток 10
        assertThat(statuses).as("статусы ответов").allMatch(code -> code < 500);
        assertInvariants(s, "10");
    }

    @Test
    void strictReservations_exactlyThreeOfFourSucceed() throws Exception {
        StockRow s = createStock("10");
        List<Integer> statuses = parallel(4, () -> reserve(s, "3", false)); // «всё или ничего»: 3 по 3 = 9, четвёртому не хватает
        assertThat(statuses).allMatch(code -> code < 500);
        assertThat(statuses.stream().filter(code -> code == 201).count()).as("успешных резервов").isEqualTo(3);
        assertInvariants(s, "9");
    }
}
