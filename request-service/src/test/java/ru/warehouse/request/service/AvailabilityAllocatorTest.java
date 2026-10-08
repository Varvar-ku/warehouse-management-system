// src/test/java/ru/warehouse/request/service/AvailabilityAllocatorTest.java
package ru.warehouse.request.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityAllocatorTest {
    private static BigDecimal n(String v) { return new BigDecimal(v); }

    @Test
    void sameProductInTwoLines_availableIsSplitInOrder() {
        UUID p = UUID.randomUUID();
        List<BigDecimal> approved = AvailabilityAllocator.allocate(
                List.of(p, p), List.of(n("10"), n("5")), Map.of(p, n("12")));
        assertThat(approved).containsExactly(n("10"), n("2")); // первая строка забрала своё, второй осталось 2
    }

    @Test
    void unavailableProduct_getsZero() {
        UUID p = UUID.randomUUID();
        List<BigDecimal> approved = AvailabilityAllocator.allocate(List.of(p), List.of(n("3")), Map.of());
        assertThat(approved).containsExactly(BigDecimal.ZERO);
    }

    @Test
    void enoughStock_approvesEverything() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<BigDecimal> approved = AvailabilityAllocator.allocate(
                List.of(a, b), List.of(n("2"), n("7")), Map.of(a, n("100"), b, n("7")));
        assertThat(approved).containsExactly(n("2"), n("7"));
    }
}