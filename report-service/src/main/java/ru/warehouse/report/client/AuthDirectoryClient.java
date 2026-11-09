package ru.warehouse.report.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.Remote;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Справочник пользователей auth-service (нужна роль STOREKEEPER или ADMIN: пересылается JWT вызывающего). */
@Component
public class AuthDirectoryClient {

    public record UserInfo(UUID id, String login, String fullName, boolean active) {
    }

    private static final int CHUNK = 100; // auth-service принимает не больше 200 id за запрос

    private final RestClient client;

    public AuthDirectoryClient(@Qualifier("authRestClient") RestClient client) {
        this.client = client;
    }

    public Map<UUID, UserInfo> users(Collection<UUID> ids) {
        Map<UUID, UserInfo> result = new HashMap<>();
        List<UUID> list = ids.stream().filter(Objects::nonNull).distinct().toList();
        for (int i = 0; i < list.size(); i += CHUNK) {
            String param = list.subList(i, Math.min(i + CHUNK, list.size())).stream()
                    .map(UUID::toString).collect(Collectors.joining(","));
            List<UserInfo> part = Remote.call("auth-service", () -> client.get()
                    .uri(uri -> uri.path("/api/v1/directory/users").queryParam("ids", param).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UserInfo>>() {
                    }));
            if (part != null) {
                part.forEach(u -> result.put(u.id(), u));
            }
        }
        return result;
    }
}
