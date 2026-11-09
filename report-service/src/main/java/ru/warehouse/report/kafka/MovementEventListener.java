package ru.warehouse.report.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.warehouse.report.service.MovementIngestService;

@Component
public class MovementEventListener {

    private final MovementIngestService ingestService;

    public MovementEventListener(MovementIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @KafkaListener(topics = "${app.report.kafka.movements-topic}")
    public void onMessage(ConsumerRecord<String, String> record) {
        if (record.value() != null) {
            ingestService.handle(record.value());
        }
    }
}
