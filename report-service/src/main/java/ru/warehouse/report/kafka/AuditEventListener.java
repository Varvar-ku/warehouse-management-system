package ru.warehouse.report.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.warehouse.report.service.AuditDispatcher;

/** Читает общий поток аудита; из него строятся отчёты по комплектации, заявкам и расхождениям. */
@Component
public class AuditEventListener {

    private final AuditDispatcher dispatcher;

    public AuditEventListener(AuditDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(topics = "${app.report.kafka.audit-topic}")
    public void onMessage(ConsumerRecord<String, String> record) {
        if (record.value() != null) {
            dispatcher.handle(record.value());
        }
    }
}
