// idempotency/IdempotencyConfig.java
package ru.warehouse.inventory.idempotency;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableScheduling
class IdempotencyConfig {

    @Bean
    IdempotencyFilter idempotencyFilter(IdempotencyStore store, IdempotencyProperties props) {
        // бин-фильтр регистрируется в сервлет-контейнере автоматически
        return new IdempotencyFilter(store, props, JsonMapper.builder().build());
    }

    /** Раз в час удаляет устаревшие ключи. */
    @Slf4j
    @Component
    @RequiredArgsConstructor
    static class Cleanup {
        private final IdempotencyStore store;
        private final IdempotencyProperties props;

        @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
        void run() {
            try {
                int removed = store.cleanup(props.retention());
                if (removed > 0) log.info("Удалено устаревших ключей идемпотентности: {}", removed);
            } catch (RuntimeException e) {
                log.warn("Не удалось очистить устаревшие ключи идемпотентности: {}", e.getMessage());
            }
        }
    }
}
