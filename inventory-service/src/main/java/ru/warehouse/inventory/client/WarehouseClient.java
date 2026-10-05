// WarehouseClient.java
package ru.warehouse.inventory.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.inventory.client.WarehouseContract.*;
import java.util.List;
import java.util.UUID;

@Component
public class WarehouseClient {
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

    public SectionInfo section(UUID id) {
        SectionInfo info = Remote.call("warehouse-service", () -> client.get()
                .uri("/api/v1/sections/{id}", id).retrieve().body(SectionInfo.class));
        if (info == null) throw ApiException.upstream("warehouse-service вернул пустой ответ");
        return info;
    }

    /** Расчёт рекомендуемых секций (нужна роль STOREKEEPER: пересылается JWT пользователя). */
    public List<PlacementResult> recommend(PlacementRequest request) {
        List<PlacementResult> results = Remote.call("warehouse-service", () -> client.post()
                .uri("/api/v1/placement/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<List<PlacementResult>>() {}));
        if (results == null) throw ApiException.upstream("warehouse-service вернул пустой ответ");
        return results;
    }
}