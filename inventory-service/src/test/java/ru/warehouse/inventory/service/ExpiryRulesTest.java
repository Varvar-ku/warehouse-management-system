// ExpiryRulesTest.java
package ru.warehouse.inventory.service;

import org.junit.jupiter.api.Test;
import ru.warehouse.inventory.repository.StockRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiryRulesTest {
    // "сейчас" в часовом поясе сервера, чтобы тест не зависел от машины
    private static final OffsetDateTime NOW =
            ZonedDateTime.of(2026, 10, 28, 15, 30, 0, 0, ZoneId.systemDefault()).toOffsetDateTime();
    private static final OffsetDateTime TODAY_START = StockRepository.expiryCutoff(NOW);

    @Test
    void noExpiryIsNeverExpired() {
        assertFalse(ExpiryRules.isExpired(null, NOW));
    }

    @Test
    void yesterdayIsExpired() {
        assertTrue(ExpiryRules.isExpired(TODAY_START.minusDays(1), NOW));
        assertTrue(ExpiryRules.isExpired(TODAY_START.minusNanos(1), NOW));
    }

    @Test
    void todayIsAcceptedBecauseExpiryDateIsInclusive() {
        assertFalse(ExpiryRules.isExpired(TODAY_START, NOW));          // дата без времени = полночь сегодняшнего дня
        assertFalse(ExpiryRules.isExpired(NOW.minusHours(5), NOW));    // уже прошедшее время сегодняшних суток
    }

    @Test
    void futureIsAccepted() {
        assertFalse(ExpiryRules.isExpired(NOW.plusDays(30), NOW));
    }
}
