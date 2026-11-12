// SectionRulesTest.java
package ru.warehouse.inventory.service;

import org.junit.jupiter.api.Test;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.inventory.client.WarehouseContract.SectionInfo;
import ru.warehouse.inventory.entity.ReceiptItem.TargetDecision;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SectionRulesTest {
    private static SectionInfo section(String status, boolean unassigned) {
        return new SectionInfo(UUID.randomUUID(), UUID.randomUUID(), "MA-2", "Test", status, unassigned);
    }

    @Test
    void activeSectionIsAccepted() {
        assertDoesNotThrow(() -> SectionRules.requirePlaceable(section("ACTIVE", false), TargetDecision.RECOMMENDED));
    }

    @Test
    void blockedAndArchivedSectionsAreRejected() {
        for (String status : new String[]{"BLOCKED", "ARCHIVED"}) {
            assertThrows(ApiException.class,
                    () -> SectionRules.requirePlaceable(section(status, false), TargetDecision.ALTERNATIVE));
        }
    }

    @Test
    void unassignedZoneNeedsExplicitDecision() {
        SectionInfo zone = section("ACTIVE", true);
        assertThrows(ApiException.class, () -> SectionRules.requirePlaceable(zone, TargetDecision.RECOMMENDED));
        assertThrows(ApiException.class, () -> SectionRules.requirePlaceable(zone, TargetDecision.ALTERNATIVE));
        assertDoesNotThrow(() -> SectionRules.requirePlaceable(zone, TargetDecision.UNASSIGNED_ZONE));
    }

    @Test
    void newTargetGetsMatchingDecisionAndIsValidated() {
        assertEquals(TargetDecision.ALTERNATIVE, SectionRules.decisionForNewTarget(section("ACTIVE", false)));
        assertEquals(TargetDecision.UNASSIGNED_ZONE, SectionRules.decisionForNewTarget(section("ACTIVE", true)));
        assertThrows(ApiException.class, () -> SectionRules.decisionForNewTarget(section("BLOCKED", false)));
    }

    @Test
    void blocksReservationUnlessSectionIsActive() {
        assertEquals(false, SectionRules.blocksReservation(section("ACTIVE", false)));
        assertEquals(true, SectionRules.blocksReservation(section("BLOCKED", false)));
        assertEquals(true, SectionRules.blocksReservation(section("ARCHIVED", false)));
    }

    /** ACCEPT_ACTUAL использует те же правила: заблокированная после размещения секция принять товар не может. */
    @Test
    void acceptActualRecheckUsesSameRules() {
        assertThrows(ApiException.class,
                () -> SectionRules.requirePlaceable(section("BLOCKED", false), TargetDecision.RECOMMENDED));
        assertThrows(ApiException.class,
                () -> SectionRules.requirePlaceable(section("ACTIVE", true), TargetDecision.ALTERNATIVE));
    }
}
