package ru.warehouse.notification.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Обработка ошибок потребителя: 3 повтора с паузой 1 с, затем сообщение уходит в топик {@code <topic>.DLT}.
 * Бин CommonErrorHandler подхватывается автоконфигурацией Spring Boot для всех @KafkaListener.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
