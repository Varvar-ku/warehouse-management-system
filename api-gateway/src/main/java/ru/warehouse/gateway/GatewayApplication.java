package ru.warehouse.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.warehouse.common.EnableWarehouseCommon;

@SpringBootApplication
@EnableWarehouseCommon   // тот же JWT-фильтр и trace-id, что и в сервисах
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}