// CatalogClient.java
package ru.warehouse.request.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.Remote;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class CatalogClient {
    /** Минимум полей номенклатуры, нужных заявке (лишние поля ответа игнорируются). */
    public record Product(UUID id, String sku, String name, String unitCode, Boolean unitAllowFractions, boolean active) {}

    private final RestClient client;

    public CatalogClient(@Qualifier("catalogRestClient") RestClient client) {
        this.client = client;
    }

    public Map<UUID, Product> products(Collection<UUID> ids) {
        Map<UUID, Product> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        String joined = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
        List<Product> list = Remote.call("catalog-service", () -> client.get()
                .uri(b -> b.path("/api/v1/products/batch").queryParam("ids", joined).build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<Product>>() {}));
        if (list != null) list.forEach(p -> result.put(p.id(), p));
        return result;
    }
}