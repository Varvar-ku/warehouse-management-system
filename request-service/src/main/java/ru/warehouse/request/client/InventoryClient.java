// InventoryClient.java
package ru.warehouse.request.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.Remote;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.request.client.InventoryContract.*;
import java.util.List;
import java.util.UUID;

/** Вызовы inventory-service с JWT пользователя (нужна роль STOREKEEPER). */
@Component
public class InventoryClient {
    private static final String SERVICE = "inventory-service";
    private final RestClient client;

    public InventoryClient(@Qualifier("inventoryRestClient") RestClient client) {
        this.client = client;
    }

    public List<AvailabilityItem> availability(UUID warehouseId, List<AvailabilityLine> lines) {
        List<AvailabilityItem> result = Remote.call(SERVICE, () -> client.post()
                .uri("/api/v1/reservations/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AvailabilityRequest(warehouseId, lines))
                .retrieve()
                .body(new ParameterizedTypeReference<List<AvailabilityItem>>() {}));
        if (result == null) throw ApiException.upstream("inventory-service вернул пустой ответ");
        return result;
    }

    /** Idempotency-Key обязателен: повтор после сбоя вернёт сохранённый ответ, а не создаст второй резерв. */
    public ReserveResponse reserve(ReserveRequest request, String idempotencyKey) {
        ReserveResponse result = Remote.call(SERVICE, () -> client.post()
                .uri("/api/v1/reservations")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ReserveResponse.class));
        if (result == null) throw ApiException.upstream("inventory-service вернул пустой ответ");
        return result;
    }

    /** Снятие резерва идемпотентно по природе: повторный вызов находит 0 активных резервов. */
    public ReleaseResponse release(UUID requestId) {
        ReleaseResponse result = Remote.call(SERVICE, () -> client.post()
                .uri("/api/v1/reservations/release")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReleaseRequest(requestId, null))
                .retrieve()
                .body(ReleaseResponse.class));
        if (result == null) throw ApiException.upstream("inventory-service вернул пустой ответ");
        return result;
    }

    public List<ReservationDto> reservations(UUID requestId) {
        List<ReservationDto> result = Remote.call(SERVICE, () -> client.get()
                .uri("/api/v1/reservations?requestId={id}", requestId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ReservationDto>>() {}));
        return result == null ? List.of() : result;
    }
}