// RequestStatus.java
package ru.warehouse.request.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Статусы заявки и допустимые переходы (разд. 8 ТЗ плюс четыре добавленных, помечены «+»). */
public enum RequestStatus {
    DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, PARTIALLY_AVAILABLE, RESERVED, RESERVED_PARTIAL,
    PICKING, PICKED, PICKING_WITH_SHORTAGE, READY_FOR_ISSUE, ISSUED, REJECTED, CANCELLED, CLOSED;

    private static final Map<RequestStatus, Set<RequestStatus>> NEXT = new EnumMap<>(RequestStatus.class);

    static {
        NEXT.put(DRAFT, EnumSet.of(SUBMITTED));
        NEXT.put(SUBMITTED, EnumSet.of(UNDER_REVIEW, CANCELLED));
        NEXT.put(UNDER_REVIEW, EnumSet.of(APPROVED, PARTIALLY_AVAILABLE, REJECTED));
        NEXT.put(APPROVED, EnumSet.of(RESERVED, CANCELLED /* + */, UNDER_REVIEW /* + повторная проверка */));
        NEXT.put(PARTIALLY_AVAILABLE, EnumSet.of(RESERVED_PARTIAL, REJECTED, CANCELLED, UNDER_REVIEW /* + */));
        NEXT.put(RESERVED, EnumSet.of(PICKING, CANCELLED /* + */));
        NEXT.put(RESERVED_PARTIAL, EnumSet.of(PICKING, CANCELLED));
        NEXT.put(PICKING, EnumSet.of(PICKED, PICKING_WITH_SHORTAGE, CANCELLED));
        NEXT.put(PICKED, EnumSet.of(READY_FOR_ISSUE));
        NEXT.put(PICKING_WITH_SHORTAGE, EnumSet.of(READY_FOR_ISSUE, CANCELLED));
        NEXT.put(READY_FOR_ISSUE, EnumSet.of(ISSUED));
        NEXT.put(ISSUED, EnumSet.of(CLOSED));
        NEXT.put(REJECTED, EnumSet.of(CLOSED));
        NEXT.put(CANCELLED, EnumSet.of(CLOSED));
    }

    public boolean canTransitionTo(RequestStatus target) {
        return NEXT.getOrDefault(this, Set.of()).contains(target);
    }
}