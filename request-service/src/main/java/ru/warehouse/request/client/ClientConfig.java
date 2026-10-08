// ClientConfig.java
package ru.warehouse.request.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.ServiceRestClients;

@Configuration
class ClientConfig {
    @Bean RestClient catalogRestClient(ServicesProperties p) { return ServiceRestClients.create(p.catalogUrl()); }
    @Bean RestClient warehouseRestClient(ServicesProperties p) { return ServiceRestClients.create(p.warehouseUrl()); }
    @Bean RestClient inventoryRestClient(ServicesProperties p) { return ServiceRestClients.create(p.inventoryUrl()); }
}