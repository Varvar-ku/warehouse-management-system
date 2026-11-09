package ru.warehouse.request.numbering;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.warehouse.common.numbering.DocumentNumbers;
import java.time.ZoneId;

@Configuration
class NumberingConfig {

    /** Часовой пояс, по которому определяется год в номерах документов (app.numbering.zone). */
    @Bean
    DocumentNumbers documentNumbers(JdbcClient jdbc, @Value("${app.numbering.zone:Europe/Moscow}") String zone) {
        return new DocumentNumbers(jdbc, ZoneId.of(zone));
    }
}
