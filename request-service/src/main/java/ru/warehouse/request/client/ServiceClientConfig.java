package ru.warehouse.request.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.ServiceRestClients;

/**
 * Клиент inventory-service для внутренних операций (P1-10): токен не пересылается из запроса пользователя,
 * а выпускается на каждый вызов (ServiceTokenIssuer). Фабрика и таймауты общие с остальными клиентами
 * (2 с и 10 с); отличие одно: пользовательский JWT не пересылается.
 */
@Configuration
class ServiceClientConfig {

    @Bean("inventoryServiceRestClient")
    RestClient inventoryServiceRestClient(@Value("${app.services.inventory-url:http://localhost:8084}") String url) {
        return ServiceRestClients.createWithoutUserToken(url);
    }
}
