package ru.warehouse.auth.it;

import java.util.List;

/** Коды 401 и 403 по ролям для эндпоинтов auth-service. */
class RoleAccessIntegrationTest extends RoleAccessTestSupport {
    private static final String WAREHOUSE = "00000000-0000-0000-0000-0000000000a1";
    private static final String PRODUCT = "00000000-0000-0000-0000-0000000000b1";
    private static final String SECTION = "\"" + ID + "\"";

    @Override
    List<Endpoint> endpoints() {
        return List.of(
                get("/api/v1/users", ADMIN),
                get("/api/v1/users/" + ID, ADMIN),
                ep("POST", "/api/v1/users", "{\"login\":\"t-user-1\",\"password\":\"Passw0rd!1\",\"fullName\":\"n\",\"roles\":[\"USER\"]}", ADMIN),
                ep("PATCH", "/api/v1/users/" + ID, "{}", ADMIN),
                ep("POST", "/api/v1/users/" + ID + "/reset-password", "{\"newPassword\":\"Passw0rd!2\"}", ADMIN),
                get("/api/v1/roles", ADMIN),
                get("/api/v1/directory/workers", STOREKEEPER, ADMIN),
                get("/api/v1/directory/users", STOREKEEPER, ADMIN),
                get("/actuator/metrics", ADMIN)
        );
    }
}
