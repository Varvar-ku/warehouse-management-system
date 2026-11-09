package ru.warehouse.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.warehouse.common.EnableWarehouseCommon;

@SpringBootApplication
@EnableWarehouseCommon // единый формат ошибок, trace-id, JWT-защита
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}