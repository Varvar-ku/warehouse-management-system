package ru.warehouse.inventory.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.inventory.dto.OperationDtos.*;
import ru.warehouse.inventory.service.ReservationService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B3, B1: что можно резервировать. Просроченное, из заблокированной секции, из «Нераспределено» и строки «на проверке»
 * не резервируются и не входят в проверку наличия; из годного остатка первым берётся тот, у которого срок короче.
 */
class ReservationRulesIntegrationTest extends IntegrationTestBase {
    @Autowired ReservationService reservations;

    private final UUID actor = UUID.randomUUID();

    private ReserveResponse reserve(UUID warehouse, UUID product, String quantity, boolean allowPartial) {
        return reservations.reserve(new ReserveRequest(UUID.randomUUID(), warehouse, allowPartial,
                List.of(new ReserveItemRequest(UUID.randomUUID(), product, new BigDecimal(quantity)))), actor);
    }

    private BigDecimal available(UUID warehouse, UUID product) {
        return reservations.availability(new AvailabilityRequest(warehouse,
                List.of(new AvailabilityItemRequest(product, BigDecimal.ONE)))).get(0).available();
    }

    private static UUID allocatedStock(ReserveResponse r) {
        List<Allocation> a = r.items().get(0).allocations();
        assertThat(a).hasSize(1);
        return a.get(0).stockId();
    }

    @Test
    void expiredStock_isNotReserved_andNotCountedAsAvailable() {
        UUID w = UUID.randomUUID(), p = UUID.randomUUID();
        StockFixtures.stock(jdbc, w, p, StockFixtures.Spec.plain("A-1", "OLD", "10").expiring(OffsetDateTime.now().minusDays(1)));
        UUID good = StockFixtures.stock(jdbc, w, p, StockFixtures.Spec.plain("A-2", "NEW", "10").expiring(OffsetDateTime.now().plusDays(30)));

        assertThat(available(w, p)).isEqualByComparingTo("10");
        assertThat(allocatedStock(reserve(w, p, "5", false))).isEqualTo(good);
        assertThatThrownBy(() -> reserve(w, p, "6", false))   // доступно только 5: просроченные 10 не в счёт
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    void shorterShelfLife_isReservedFirst() {
        UUID w = UUID.randomUUID(), p = UUID.randomUUID();
        StockFixtures.stock(jdbc, w, p, StockFixtures.Spec.plain("A-1", "LATE", "10").expiring(OffsetDateTime.now().plusDays(40)));
        UUID soon = StockFixtures.stock(jdbc, w, p, StockFixtures.Spec.plain("A-2", "SOON", "10").expiring(OffsetDateTime.now().plusDays(10)));

        assertThat(allocatedStock(reserve(w, p, "5", false))).isEqualTo(soon);
    }

    @Test
    void blockedSection_unassignedZone_andHeldStock_areExcluded() {
        UUID w = UUID.randomUUID(), p = UUID.randomUUID();
        StockFixtures.stock(jdbc, w, p, StockFixtures.Spec.plain("B-1", "BLOCKED", "10").blocked());
        StockFixtures.stock(jdbc, w, p, StockFixtures.Spec.plain("UNASSIGNED", "UNASSIGNED", "10").unassigned());
        StockFixtures.stock(jdbc, w, p, StockFixtures.Spec.plain("H-1", "HELD", "10").held());
        UUID usable = StockFixtures.stock(jdbc, w, p, StockFixtures.Spec.plain("C-1", "OK", "4"));

        assertThat(available(w, p)).isEqualByComparingTo("4");
        ReserveResponse partial = reserve(w, p, "10", true);
        assertThat(partial.fullyReserved()).isFalse();
        assertThat(partial.items().get(0).reserved()).isEqualByComparingTo("4");
        assertThat(allocatedStock(partial)).isEqualTo(usable);
    }

    @Test
    void releaseIsIdempotent() {
        UUID w = UUID.randomUUID(), p = UUID.randomUUID();
        StockFixtures.stock(jdbc, w, p, StockFixtures.Spec.plain("A-1", "R", "10"));
        UUID requestId = UUID.randomUUID();
        reservations.reserve(new ReserveRequest(requestId, w, false,
                List.of(new ReserveItemRequest(UUID.randomUUID(), p, new BigDecimal("6")))), actor);
        assertThat(available(w, p)).isEqualByComparingTo("4");

        ReleaseResponse first = reservations.release(new ReleaseRequest(requestId, null), actor);
        ReleaseResponse second = reservations.release(new ReleaseRequest(requestId, null), actor);

        assertThat(first.releasedReservations()).isEqualTo(1);
        assertThat(second.releasedReservations()).isZero();
        assertThat(available(w, p)).isEqualByComparingTo("10");
    }
}
