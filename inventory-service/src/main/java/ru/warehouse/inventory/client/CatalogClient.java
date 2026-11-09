// CatalogClient.java
package ru.warehouse.inventory.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.Remote;
import ru.warehouse.inventory.client.CatalogContract.CatalogProduct;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class CatalogClient {
    private final RestClient client;

    public CatalogClient(@Qualifier("catalogRestClient") RestClient client) {
        this.client = client;
    }

    /** Номенклатура пакетом (не более 200 id). Несуществующие id в результате отсутствуют. */
    public Map<UUID, CatalogProduct> products(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        String joined = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
        List<CatalogProduct> list = Remote.call("catalog-service", () -> client.get()
                .uri(b -> b.path("/api/v1/products/batch").queryParam("ids", joined).build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<CatalogProduct>>() {}));
        Map<UUID, CatalogProduct> result = new HashMap<>();
        if (list != null) list.forEach(p -> result.put(p.id(), p));
        return result;
    }
}