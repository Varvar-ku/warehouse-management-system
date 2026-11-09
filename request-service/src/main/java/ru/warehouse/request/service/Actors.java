package ru.warehouse.request.service;

import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import java.util.UUID;

/** Разбор пользователя текущего запроса из JWT (раньше метод actor(Jwt) дублировался в контроллерах). */
public final class Actors {
    private Actors() {}

    public static Actor from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean storekeeper = roles != null && roles.contains("STOREKEEPER");
        boolean admin = roles != null && roles.contains("ADMIN");
        return new Actor(UUID.fromString(jwt.getSubject()), storekeeper, admin);
    }
}
