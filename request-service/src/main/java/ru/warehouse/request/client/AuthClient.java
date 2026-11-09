package ru.warehouse.request.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.Remote;
import java.util.List;
import java.util.UUID;

@Component
public class AuthClient {
    public record WorkerInfo(UUID id, String login, String fullName) {}

    private final RestClient client;

    public AuthClient(@Qualifier("authRestClient") RestClient client) {
        this.client = client;
    }

    /** Активные пользователи с ролью WAREHOUSE_WORKER (нужна роль STOREKEEPER или ADMIN: пересылается JWT). */
    public List<WorkerInfo> workers() {
        List<WorkerInfo> result = Remote.call("auth-service", () -> client.get()
                .uri("/api/v1/directory/workers")
                .retrieve()
                .body(new ParameterizedTypeReference<List<WorkerInfo>>() {}));
        return result == null ? List.of() : result;
    }
}