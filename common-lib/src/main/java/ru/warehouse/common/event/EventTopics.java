package ru.warehouse.common.event;

/** Kafka topic names. Version is part of the name: a breaking schema change means a new topic. */
public final class EventTopics {

    /** Immutable audit trail events (consumed by audit-service). */
    public static final String AUDIT = "wms.audit.v1";
    public static final String STOCK_MOVEMENTS = "wms.stock-movements.v1";
    /** Смена статуса секции (warehouse-service пишет, inventory-service читает). */
    public static final String SECTION_STATUS = SectionStatusEvent.TOPIC;

    /** Топик «мёртвых» сообщений для исходного топика (по соглашению Spring Kafka). */
    public static String dlt(String topic) {
        return topic + ".DLT";
    }

    private EventTopics() {
    }
}
