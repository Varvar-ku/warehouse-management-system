// RequestStatus.java
package ru.warehouse.request.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Статусы заявки и допустимые переходы (разд. 8 ТЗ плюс добавленные, помечены «+»).
 * UNDER_REVIEW (B6) из модели убран: проверка наличия сразу даёт APPROVED или PARTIALLY_AVAILABLE.
 * Константа осталась только потому, что в неизменяемой истории статусов есть старые записи с ней;
 * переходов в неё и из неё нет.
 */
public enum RequestStatus {
    DRAFT, SUBMITTED, /** @deprecated только для старых записей истории */ @Deprecated UNDER_REVIEW, APPROVED, PARTIALLY_AVAILABLE, RESERVED, RESERVED_PARTIAL,
    PICKING, PICKED, PICKING_WITH_SHORTAGE, READY_FOR_ISSUE, ISSUED, REJECTED, CANCELLED, CLOSED;

    private static final Map<RequestStatus, Set<RequestStatus>> NEXT = new EnumMap<>(RequestStatus.class);

    static {
        NEXT.put(DRAFT, EnumSet.of(SUBMITTED));
        NEXT.put(SUBMITTED, EnumSet.of(APPROVED, PARTIALLY_AVAILABLE, REJECTED /* + */, CANCELLED));
        NEXT.put(APPROVED, EnumSet.of(RESERVED, PARTIALLY_AVAILABLE /* + повторная проверка */,
                REJECTED /* + */, CANCELLED /* + */));
        NEXT.put(PARTIALLY_AVAILABLE, EnumSet.of(RESERVED_PARTIAL, APPROVED /* + повторная проверка */,
                REJECTED, CANCELLED));
        NEXT.put(RESERVED, EnumSet.of(PICKING, CANCELLED /* + */));
        NEXT.put(RESERVED_PARTIAL, EnumSet.of(PICKING, CANCELLED));
        NEXT.put(PICKING, EnumSet.of(PICKED, PICKING_WITH_SHORTAGE, CANCELLED));
        // + PICKING: возврат в сборку при исправлении ошибочного ввода (переоткрытие строки, S3), пока не «готово к выдаче»
        NEXT.put(PICKED, EnumSet.of(READY_FOR_ISSUE, PICKING));
        NEXT.put(PICKING_WITH_SHORTAGE, EnumSet.of(READY_FOR_ISSUE, CANCELLED, PICKING));
        NEXT.put(READY_FOR_ISSUE, EnumSet.of(ISSUED, CANCELLED /* только если ничего не собрано, проверяется в RequestService.cancel */));
        NEXT.put(ISSUED, EnumSet.of(CLOSED));
        NEXT.put(REJECTED, EnumSet.of(CLOSED));
        NEXT.put(CANCELLED, EnumSet.of(CLOSED));
    }

    public boolean canTransitionTo(RequestStatus target) {
        return NEXT.getOrDefault(this, Set.of()).contains(target);
    }
}
