// SectionStatusListener.java
package ru.warehouse.inventory.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.warehouse.common.event.SectionStatusEvent;
import ru.warehouse.inventory.service.SectionStatusService;

/** События смены статуса секции из warehouse-service. Ключ сообщения - секция, поэтому порядок по секции сохраняется. */
@Component
public class SectionStatusListener {
    private final SectionStatusService service;

    public SectionStatusListener(SectionStatusService service) {
        this.service = service;
    }

    @KafkaListener(topics = SectionStatusEvent.TOPIC, groupId = "inventory-section-status",
            properties = "auto.offset.reset=earliest")
    public void onMessage(ConsumerRecord<String, String> record) {
        if (record.value() == null) return;
        service.handle(record.value());
    }
}
