// StockRepositoryHoldIT.java
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

/** B1: строка остатка «на проверке» не резервируется и не входит в проверку наличия; после снятия блокировки возвращается. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class StockRepositoryHoldIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:18");

    @Autowired StockRepository repo;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;

    static final OffsetDateTime CUTOFF = OffsetDateTime.parse("2026-11-01T00:00:00+03:00");
    final UUID warehouse = UUID.randomUUID();
    final UUID product = UUID.randomUUID();

    @Test
    void heldRowIsNotReservable() {
        UUID ok = stock("A-01", false);
        UUID held = stock("A-02", true);
        assertThat(repo.findAvailableIdsOrderedAt(warehouse, product, CUTOFF)).containsExactly(ok);
        assertThat(repo.findAvailableIdsOrderedAt(warehouse, product, CUTOFF)).doesNotContain(held);
    }

    @Test
    void heldRowIsNotCountedInTotals() {
        stock("A-01", false);   // 10
        stock("A-02", true);    // 10, на проверке
        List<StockRepository.ProductTotals> t = repo.totalsAt(warehouse, List.of(product), CUTOFF);
        assertThat(t).hasSize(1);
        assertThat(t.get(0).getTotal()).isEqualByComparingTo("10");
    }

    @Test
    void productWithOnlyHeldStockIsAbsentFromTotals() {
        stock("A-01", true);
        assertThat(repo.totalsAt(warehouse, List.of(product), CUTOFF)).isEmpty();
    }

    @Test
    void rowReturnsToCirculationAfterHoldIsReleased() {
        UUID id = stock("A-01", true);
        assertThat(repo.findAvailableIdsOrderedAt(warehouse, product, CUTOFF)).isEmpty();
        Stock s = repo.findById(id).orElseThrow();
        s.setOnHold(false);
        em.flush();
        assertThat(repo.findAvailableIdsOrderedAt(warehouse, product, CUTOFF)).containsExactly(id);
    }

    // --- помощники ---

    private UUID stock(String sectionCode, boolean onHold) {
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
        b.setBatchNumber("L-" + UUID.randomUUID());
        b.setReceivedAt(CUTOFF.minusDays(10));
        em.persist(b);

        Stock s = new Stock();
        s.setWarehouseId(warehouse);
        s.setProductId(product);
        s.setSectionId(UUID.randomUUID());
        s.setSectionCode(sectionCode);
        s.setBatch(b);
        s.setUnitCode("PCS");
        s.setQuantity(new BigDecimal("10"));
        s.setOnHold(onHold);
        s.setHoldSince(onHold ? OffsetDateTime.now() : null);
        em.persist(s);
        em.flush();
        return s.getId();
    }
}
