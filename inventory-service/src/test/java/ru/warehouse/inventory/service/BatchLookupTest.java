package ru.warehouse.inventory.service;

import org.junit.jupiter.api.Test;
import ru.warehouse.inventory.service.BatchLookup.BatchRef;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class BatchLookupTest {
    private static final OffsetDateTime EXP = OffsetDateTime.parse("2027-06-01T00:00:00Z");

    private static BatchRef batch(String unit, OffsetDateTime expiry) {
        return new BatchRef(UUID.randomUUID(), unit, expiry);
    }

    @Test
    void sameUnitAndExpiry_isCompatible() {
        assertThat(BatchLookup.mismatch(batch("PCS", EXP), "pcs", EXP)).isNull();
        assertThat(BatchLookup.mismatch(batch("KG", null), "KG", null)).isNull();
    }

    @Test
    void sameInstantInOtherOffset_isCompatible() {
        assertThat(BatchLookup.mismatch(batch("PCS", EXP), "PCS", EXP.withOffsetSameInstant(java.time.ZoneOffset.ofHours(3)))).isNull();
    }

    @Test
    void differentExpiry_isRejected() {
        assertThat(BatchLookup.mismatch(batch("PCS", EXP), "PCS", EXP.plusDays(1))).contains("годности");
        assertThat(BatchLookup.mismatch(batch("PCS", EXP), "PCS", null)).contains("годности");
        assertThat(BatchLookup.mismatch(batch("PCS", null), "PCS", EXP)).contains("годности");
    }

    @Test
    void differentUnit_isRejected() {
        assertThat(BatchLookup.mismatch(batch("PCS", EXP), "KG", EXP)).contains("единице");
    }
}
