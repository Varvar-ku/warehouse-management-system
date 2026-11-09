package ru.warehouse.inventory.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.Remote;
import ru.warehouse.common.exception.ApiException;
import java.util.List;
import java.util.UUID;

/** Справочник auth-service для inventory: проверка исполнителя задания на размещение (M7). */
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

    /** Исполнитель должен существовать, быть активным и иметь роль WAREHOUSE_WORKER. */
    public void requireActiveWorker(UUID userId) {
        if (workers().stream().noneMatch(w -> w.id().equals(userId))) {
            throw ApiException.validation("Исполнитель " + userId
                    + " не найден среди активных работников склада (роль WAREHOUSE_WORKER)");
        }
    }
}
