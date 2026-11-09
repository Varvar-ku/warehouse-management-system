// SectionRules.java
package ru.warehouse.inventory.service;

import ru.warehouse.common.exception.ApiException;
import ru.warehouse.inventory.client.WarehouseContract.SectionInfo;
import ru.warehouse.inventory.entity.ReceiptItem.TargetDecision;

/**
 * Куда можно размещать товар (B2). Статус и зона "Нераспределено" известны из SectionInfo,
 * поэтому проверка не требует вызовов соседних сервисов.
 */
public final class SectionRules {
    private SectionRules() {}

    /**
     * - секция "Нераспределено": только если кладовщик явно принял это решение для позиции (UNASSIGNED_ZONE);
     * - остальные секции: только ACTIVE (BLOCKED и ARCHIVED принимать товар не могут).
     */
    public static void requirePlaceable(SectionInfo section, TargetDecision itemDecision) {
        if (section.unassignedZone()) {
            if (itemDecision != TargetDecision.UNASSIGNED_ZONE) {
                throw ApiException.conflict("SECTION_UNASSIGNED_ZONE",
                        "Секция «" + section.code() + "» - зона «Нераспределено»: размещение туда допустимо только "
                                + "по решению кладовщика при подтверждении приёмки");
            }
            return;
        }
        if (!"ACTIVE".equalsIgnoreCase(section.status())) {
            throw ApiException.conflict("SECTION_NOT_ACTIVE",
                    "Секция «" + section.code() + "» в статусе " + section.status() + ": размещение невозможно");
        }
    }

    /** Решение кладовщика при возврате в размещение: новая целевая секция проходит ту же проверку. */
    public static TargetDecision decisionForNewTarget(SectionInfo section) {
        requirePlaceable(section, section.unassignedZone() ? TargetDecision.UNASSIGNED_ZONE : TargetDecision.ALTERNATIVE);
        return section.unassignedZone() ? TargetDecision.UNASSIGNED_ZONE : TargetDecision.ALTERNATIVE;
    }
}
