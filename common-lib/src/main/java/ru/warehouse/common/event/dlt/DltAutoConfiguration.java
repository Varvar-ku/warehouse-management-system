package ru.warehouse.common.event.dlt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Duration;
import java.util.List;

/**
 * Включается в потребителе свойством {@code wms.dlt.redrive.enabled=true}; список исходных топиков
 * задаётся в {@code wms.dlt.redrive.topics}. Регистрируется в AutoConfiguration.imports.
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "wms.dlt.redrive", name = "enabled", havingValue = "true")
@EnableScheduling
public class DltAutoConfiguration {

    @Bean
    DltRedriver dltRedriver(ConsumerFactory<?, ?> consumers,
                            KafkaTemplate<String, String> kafka,
                            @Value("${spring.application.name:service}") String applicationName,
                            @Value("${wms.dlt.redrive.topics}") List<String> topics,
                            @Value("${wms.dlt.redrive.max-redrives:5}") int maxRedrives,
                            @Value("${wms.dlt.redrive.min-age-ms:60000}") long minAgeMs) {
        return new DltRedriver(consumers, kafka, applicationName, topics, maxRedrives, Duration.ofMillis(minAgeMs));
    }

    @Bean
    DltRedriveScheduler dltRedriveScheduler(DltRedriver redriver) {
        return new DltRedriveScheduler(redriver);
    }
}
