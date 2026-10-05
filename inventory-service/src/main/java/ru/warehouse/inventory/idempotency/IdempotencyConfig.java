// idempotency/IdempotencyConfig.java
package ru.warehouse.inventory.idempotency;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableScheduling
class IdempotencyConfig {

    @Bean
    IdempotencyFilter idempotencyFilter(IdempotencyStore store, IdempotencyProperties props) {
        return new IdempotencyFilter(store, props); // бин-фильтр регистрируется в сервлет-контейнере автоматически
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
            int removed = store.cleanup(props.retention());
            if (removed > 0) log.info("Удалено устаревших ключей идемпотентности: {}", removed);
        }
    }
}
