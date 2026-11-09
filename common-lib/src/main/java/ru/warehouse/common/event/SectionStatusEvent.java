package ru.warehouse.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Смена статуса секции в warehouse-service. Читает inventory-service: из BLOCKED и ARCHIVED резервировать нельзя. */
@JsonIgnoreProperties(ignoreUnknown = true) // новые поля события не ломают старых потребителей
public record SectionStatusEvent(UUID eventId, UUID sectionId, UUID warehouseId, String sectionCode,
                                 String status, UUID actorId) {
    public static final String TOPIC = "warehouse.section-status";

    public static SectionStatusEvent of(UUID sectionId, UUID warehouseId, String sectionCode, String status, UUID actorId) {
        return new SectionStatusEvent(UUID.randomUUID(), sectionId, warehouseId, sectionCode, status, actorId);
    }
}
