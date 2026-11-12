package ru.warehouse.inventory.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.warehouse.inventory.client.WarehouseContract.SectionInfo;
import ru.warehouse.inventory.service.StockPostingService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S1: две приёмки с одним номером партии, размещение подтверждается одновременно. Должна получиться одна партия, одна
 * строка остатка с суммой, два движения RECEIPT; при разных сроках годности вторая приёмка отклоняется.
 */
class SameBatchReceiptsIntegrationTest extends IntegrationTestBase {
    @Autowired
    StockPostingService posting;

    private record Doc(UUID taskId, UUID itemId, UUID receiptId) {}

    private Doc createReceipt(UUID warehouse, UUID product, UUID section, String batchNumber, String quantity,
                              OffsetDateTime expiry) {
        UUID receipt = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        UUID task = UUID.randomUUID();
        jdbc.update("INSERT INTO receipts (id, number, warehouse_id, supplier_name, status, received_at, created_by) "
                + "VALUES (?, ?, ?, 'test', 'PLACEMENT', ?, ?)", receipt, "RC-T-" + receipt, warehouse,
                OffsetDateTime.now(), UUID.randomUUID());
        jdbc.update("INSERT INTO receipt_items (id, receipt_id, line_no, product_id, product_sku, product_name, "
                        + "product_type_code, unit_code, quantity, batch_number, expiry_at, target_section_id, "
                        + "target_section_code, target_decision, placement_status) "
                        + "VALUES (?, ?, 1, ?, 'SKU', 'name', 'TYPE', 'PCS', ?, ?, ?, ?, 'A-1', 'RECOMMENDED', 'PENDING')",
                item, receipt, product, new BigDecimal(quantity), batchNumber, expiry, section);
        jdbc.update("INSERT INTO placement_tasks (id, receipt_id, status, created_by) VALUES (?, ?, 'OPEN', ?)",
                task, receipt, UUID.randomUUID());
        return new Doc(task, item, receipt);
    }

    private Callable<Object> place(Doc doc, SectionInfo section, String quantity) {
        return () -> posting.confirmPlacement(doc.taskId(), doc.itemId(), section, new BigDecimal(quantity), null,
                UUID.randomUUID());
    }

    private void runBoth(Callable<Object> first, Callable<Object> second, boolean expectBothOk) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> a = () -> { ready.countDown(); start.await(); return first.call(); };
        Callable<Object> b = () -> { ready.countDown(); start.await(); return second.call(); };
        Future<Object> fa = pool.submit(a);
        Future<Object> fb = pool.submit(b);
        ready.await();
        start.countDown();
        try {
            fa.get();
            if (expectBothOk) fb.get();
            else assertThatThrownBy(fb::get).hasStackTraceContaining("срок годности");
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void sameBatchPlacedConcurrently_isMergedIntoOneBatchAndOneStockRow() throws Exception {
        for (int i = 0; i < 10; i++) { // повторяем: гонка проявляется не в каждом прогоне
            UUID warehouse = UUID.randomUUID();
            UUID product = UUID.randomUUID();
            UUID section = UUID.randomUUID();
            SectionInfo info = new SectionInfo(section, warehouse, "A-1", "section", "ACTIVE", false);
            Doc first = createReceipt(warehouse, product, section, "B-1", "5", null);
            Doc second = createReceipt(warehouse, product, section, "B-1", "5", null);

            runBoth(place(first, info, "5"), place(second, info, "5"), true);

            assertThat(jdbc.queryForObject("SELECT count(*) FROM batches WHERE product_id = ? AND batch_number = 'B-1'",
                    Integer.class, product)).as("партия одна").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM stock WHERE product_id = ? AND section_id = ?",
                    Integer.class, product, section)).as("строка остатка одна").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT quantity FROM stock WHERE product_id = ? AND section_id = ?",
                    BigDecimal.class, product, section)).as("остаток = сумма").isEqualByComparingTo("10");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE product_id = ? AND movement_type = 'RECEIPT'",
                    Integer.class, product)).as("движений RECEIPT два").isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM receipts WHERE id IN (?, ?) AND status = 'DONE'",
                    Integer.class, first.receiptId(), second.receiptId())).isEqualTo(2);
        }
    }

    @Test
    void sameBatchWithDifferentExpiry_isRejected() throws Exception {
        UUID warehouse = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID section = UUID.randomUUID();
        SectionInfo info = new SectionInfo(section, warehouse, "A-1", "section", "ACTIVE", false);
        Doc first = createReceipt(warehouse, product, section, "B-2", "5", OffsetDateTime.now().plusDays(30));
        Doc second = createReceipt(warehouse, product, section, "B-2", "5", OffsetDateTime.now().plusDays(60));

        // сначала первая, затем вторая: порядок гонки не важен, важно, что вторая с другим сроком отклоняется
        posting.confirmPlacement(first.taskId(), first.itemId(), info, new BigDecimal("5"), null, UUID.randomUUID());
        assertThatThrownBy(place(second, info, "5")::call).hasMessageContaining("сроком годности");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM batches WHERE product_id = ?", Integer.class, product)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM stock WHERE product_id = ?", BigDecimal.class, product))
                .isEqualByComparingTo("5");
    }
}
