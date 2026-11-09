package ru.warehouse.notification.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.warehouse.notification.service.NotificationIngestService;

@Component
public class RequestEventListener {

    private static final Logger log = LoggerFactory.getLogger(RequestEventListener.class);

    private final NotificationIngestService ingestService;

    public RequestEventListener(NotificationIngestService ingestService) {
        this.ingestService = ingestService;
    }

    /** Список топиков из app.notification.kafka.events-topics (через запятую, пробелы вокруг запятых допустимы). */
    @KafkaListener(topics = "#{'${app.notification.kafka.events-topics}'.split('\\s*,\\s*')}")
    public void onMessage(ConsumerRecord<String, String> record) {
        if (record.value() == null) {
            log.debug("Skipping empty record at {}-{}@{}", record.topic(), record.partition(), record.offset());
            return;
        }
        ingestService.handle(record.value());
    }
}
