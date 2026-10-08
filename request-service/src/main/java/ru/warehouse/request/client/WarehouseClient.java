// WarehouseClient.java
package ru.warehouse.request.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.Remote;
import ru.warehouse.common.exception.ApiException;
import java.util.UUID;

@Component
public class WarehouseClient {
    public record WarehouseInfo(UUID id, String code, String name, boolean active) {}

    private final RestClient client;

    public WarehouseClient(@Qualifier("warehouseRestClient") RestClient client) {
        this.client = client;
    }

    public WarehouseInfo warehouse(UUID id) {
        WarehouseInfo info = Remote.call("warehouse-service", () -> client.get()
                .uri("/api/v1/warehouses/{id}", id).retrieve().body(WarehouseInfo.class));
        if (info == null) throw ApiException.upstream("warehouse-service вернул пустой ответ");
        return info;
    }
}