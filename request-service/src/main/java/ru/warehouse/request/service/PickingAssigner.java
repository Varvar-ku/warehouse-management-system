// PickingAssigner.java
package ru.warehouse.request.service;

import ru.warehouse.common.exception.ApiException;
import ru.warehouse.request.entity.PickingItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Распределение количества позиции заявки работнику по свободным строкам комплектации.
 * Строка, не вошедшая целиком, разбивается: остаток остаётся свободным, назначаемая часть становится новой строкой.
 * Чистая логика без БД.
 */
final class PickingAssigner {
    private PickingAssigner() {}

    /**
     * @param freeRows свободные (PENDING, без исполнителя) строки одной позиции заявки в порядке обхода
     * @return новые строки, появившиеся при разбиении (вызывающий добавляет их в задание)
     */
    static List<PickingItem> assign(List<PickingItem> freeRows, UUID workerId, BigDecimal quantity) {
        BigDecimal remaining = quantity;
        List<PickingItem> created = new ArrayList<>();
        for (PickingItem row : freeRows) {
            if (remaining.signum() <= 0) break;
            BigDecimal take = row.getRequestedQuantity().min(remaining);
            if (take.compareTo(row.getRequestedQuantity()) == 0) {
                row.setAssignedWorkerId(workerId);            // строка целиком переходит работнику
            } else {
                row.setRequestedQuantity(row.getRequestedQuantity().subtract(take));
                PickingItem part = new PickingItem();         // назначаемая часть: отдельная строка
                part.setTask(row.getTask());
                part.setRequestItem(row.getRequestItem());
                part.setReservationId(row.getReservationId());
                part.setStockId(row.getStockId());
                part.setSectionId(row.getSectionId());
                part.setSectionCode(row.getSectionCode());
                part.setBatchId(row.getBatchId());
                part.setBatchNumber(row.getBatchNumber());
                part.setRequestedQuantity(take);
                part.setAssignedWorkerId(workerId);
                created.add(part);
            }
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            // Исключение откатывает транзакцию целиком: ни одно назначение этого запроса не сохраняется
            throw ApiException.conflict("OVER_ASSIGNMENT", "Назначается " + quantity.stripTrailingZeros().toPlainString()
                    + ", а нераспределено только " + quantity.subtract(remaining).stripTrailingZeros().toPlainString());
        }
        return created;
    }
}
