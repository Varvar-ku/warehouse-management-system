// ClientConfig.java
package ru.warehouse.inventory.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.ServiceRestClients;

@Configuration
class ClientConfig {

    @Bean
    RestClient catalogRestClient(ServicesProperties props) {
        return ServiceRestClients.create(props.catalogUrl());
    }

    @Bean
    RestClient warehouseRestClient(ServicesProperties props) {
        return ServiceRestClients.create(props.warehouseUrl());
    }

    /** M7: auth-service. Адрес читается напрямую, чтобы не менять ServicesProperties. */
    @Bean
    RestClient authRestClient(@Value("${app.services.auth-url:http://localhost:8081}") String authUrl) {
        return ServiceRestClients.create(authUrl);
    }
}
