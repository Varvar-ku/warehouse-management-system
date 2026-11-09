package ru.warehouse.common.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;


/** Enabled in a service with {@code wms.outbox.enabled=true}. Needs spring-kafka and spring-jdbc. */
@AutoConfiguration
@ConditionalOnClass({KafkaTemplate.class, JdbcClient.class})
@ConditionalOnProperty(prefix = "wms.outbox", name = "enabled", havingValue = "true")
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    OutboxPublisher outboxPublisher(JdbcClient jdbc, ObjectMapper mapper) {
        return new OutboxPublisher(jdbc, mapper);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcClient jdbc,
                            KafkaTemplate<String, String> kafka,
                            PlatformTransactionManager txManager,
                            @Value("${wms.outbox.batch-size:100}") int batchSize,
                            @Value("${wms.outbox.retention-days:7}") int retentionDays,
                            @Value("${wms.outbox.strict-order:true}") boolean strictOrder,
                            @Value("${wms.outbox.max-attempts:10}") int maxAttempts,
                            @Value("${wms.outbox.stuck-warn-seconds:60}") long stuckWarnSeconds) {
        return new OutboxRelay(jdbc, kafka, txManager, batchSize, retentionDays, strictOrder, maxAttempts,
                Duration.ofSeconds(stuckWarnSeconds));
    }
}
