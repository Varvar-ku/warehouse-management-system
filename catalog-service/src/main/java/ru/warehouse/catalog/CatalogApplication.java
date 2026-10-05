package ru.warehouse.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.warehouse.common.EnableWarehouseCommon;

@SpringBootApplication
@EnableWarehouseCommon   // единый формат ошибок, trace-id, защита по JWT
public class CatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}