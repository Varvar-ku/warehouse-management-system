package ru.warehouse.report.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.ServiceRestClients;

@Configuration
class ClientConfig {
    @Bean
    RestClient authRestClient(ServicesProperties p) {
        return ServiceRestClients.create(p.authUrl());
    }
}
