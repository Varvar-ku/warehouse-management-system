package ru.warehouse.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import ru.warehouse.common.EnableWarehouseCommon;

@SpringBootApplication
@EnableWarehouseCommon        // единый формат ошибок, trace-id, JWT-защита
@ConfigurationPropertiesScan  // подхватывает ServicesProperties
public class InventoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}