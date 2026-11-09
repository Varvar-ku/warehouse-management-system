// StockRepositoryReservableIT.java
package ru.warehouse.inventory.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.warehouse.inventory.entity.Batch;
import ru.warehouse.inventory.entity.Stock;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * B3: резерв и проверка наличия не берут просроченное и "Нераспределено".
 * Нужен реальный PostgreSQL (в схеме IS JSON и gen_random_uuid), поэтому Testcontainers, а не H2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class StockRepositoryReservableIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:18");

    @Autowired StockRepository repo;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;

    // "Сегодня" для теста: граница годности - начало суток
    static final OffsetDateTime CUTOFF = OffsetDateTime.parse("2026-10-28T00:00:00+03:00");
    final UUID warehouse = UUID.randomUUID();
    final UUID product = UUID.randomUUID();

    @Test
    void expiredBatchIsNotReservable() {
        UUID ok = stock("A-01", batch("L1", CUTOFF.plusDays(5), CUTOFF.minusDays(30)), false);
        stock("A-02", batch("L2", CUTOFF.minusSeconds(1), CUTOFF.minusDays(60)), false);
        assertThat(repo.findAvailableIdsOrderedAt(warehouse, product, CUTOFF)).containsExactly(ok);
    }

    @Test
    void lastDayOfExpiryIsInclusive() {
        // срок "годен до" 28.10 хранится как полночь 28.10: в этот день партия ещё годна
        UUID today = stock("A-01", batch("L1", CUTOFF, CUTOFF.minusDays(30)), false);
        assertThat(repo.findAvailableIdsOrderedAt(warehouse, product, CUTOFF)).containsExactly(today);
    }

    @Test
    void unassignedZoneIsNotReservable() {
        UUID ok = stock("A-01", batch("L1", null, CUTOFF.minusDays(10)), false);
        stock("UNASSIGNED", batch("L2", null, CUTOFF.minusDays(20)), true);
        assertThat(repo.findAvailableIdsOrderedAt(warehouse, product, CUTOFF)).containsExactly(ok);
    }

    @Test
    void orderIsExpiryThenFifoWithNullExpiryLast() {
        UUID noExpiry = stock("A-01", batch("L1", null, CUTOFF.minusDays(90)), false);
        UUID late = stock("A-02", batch("L2", CUTOFF.plusDays(30), CUTOFF.minusDays(10)), false);
        UUID soon = stock("A-03", batch("L3", CUTOFF.plusDays(2), CUTOFF.minusDays(5)), false);
        assertThat(repo.findAvailableIdsOrderedAt(warehouse, product, CUTOFF))
                .containsExactly(soon, late, noExpiry);
    }

    @Test
    void totalsIgnoreExcludedRows() {
        stock("A-01", batch("L1", CUTOFF.plusDays(5), CUTOFF.minusDays(30)), false);          // 10
        stock("A-02", batch("L2", CUTOFF.minusDays(1), CUTOFF.minusDays(60)), false);         // просрочено
        stock("UNASSIGNED", batch("L3", null, CUTOFF.minusDays(20)), true);                   // Нераспределено
        List<StockRepository.ProductTotals> t = repo.totalsAt(warehouse, List.of(product), CUTOFF);
        assertThat(t).hasSize(1);
        assertThat(t.get(0).getTotal()).isEqualByComparingTo("10");
    }

    @Test
    void productWithOnlyExcludedStockIsAbsentFromTotals() {
        stock("UNASSIGNED", batch("L1", null, CUTOFF.minusDays(20)), true);
        assertThat(repo.totalsAt(warehouse, List.of(product), CUTOFF)).isEmpty();
    }

    // --- помощники ---

    private Batch batch(String number, OffsetDateTime expiry, OffsetDateTime received) {
        UUID receiptId = UUID.randomUUID();
        jdbc.update("insert into receipts (id, number, warehouse_id, supplier_name, received_at, created_by) "
                        + "values (?, ?, ?, 'test', now(), ?)",
                receiptId, "RC-T-" + UUID.randomUUID(), warehouse, UUID.randomUUID());
        Batch b = new Batch();
        b.setProductId(product);
        b.setProductSku("SKU");
        b.setProductName("Test");
        b.setUnitCode("PCS");
        b.setUnitAllowFractions(false);
        b.setReceiptId(receiptId);
        b.setBatchNumber(number);
        b.setReceivedAt(received);
        b.setExpiryAt(expiry);
        em.persist(b);
        return b;
    }

    private UUID stock(String sectionCode, Batch batch, boolean unassigned) {
        Stock s = new Stock();
        s.setWarehouseId(warehouse);
        s.setProductId(product);
        s.setSectionId(UUID.randomUUID());
        s.setSectionCode(sectionCode);
        s.setUnassignedZone(unassigned);
        s.setBatch(batch);
        s.setUnitCode("PCS");
        s.setQuantity(new BigDecimal("10"));
        em.persist(s);
        em.flush();
        return s.getId();
    }
}
