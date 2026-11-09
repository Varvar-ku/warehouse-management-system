package ru.warehouse.request.it;

import java.util.List;

/** Коды 401 и 403 по ролям для эндпоинтов request-service. */
class RoleAccessIntegrationTest extends RoleAccessTestSupport {
    private static final String WAREHOUSE = "00000000-0000-0000-0000-0000000000a1";
    private static final String PRODUCT = "00000000-0000-0000-0000-0000000000b1";
    private static final String SECTION = "\"" + ID + "\"";

    @Override
    List<Endpoint> endpoints() {
        return List.of(
                ep("POST", "/api/v1/requests", "{\"warehouseId\":" + WAREHOUSE + ",\"items\":[{\"productId\":" + PRODUCT + ",\"quantity\":1}]}", USER),
                get("/api/v1/requests", USER, STOREKEEPER, ADMIN),
                get("/api/v1/requests/" + ID, USER, STOREKEEPER, ADMIN),
                get("/api/v1/requests/" + ID + "/history", USER, STOREKEEPER, ADMIN),
                get("/api/v1/requests/usage?productId=" + ID, STOREKEEPER, ADMIN),
                ep("PATCH", "/api/v1/requests/" + ID, "{}", USER),
                ep("DELETE", "/api/v1/requests/" + ID, null, USER),
                ep("POST", "/api/v1/requests/" + ID + "/submit", null, USER),
                ep("POST", "/api/v1/requests/" + ID + "/cancel", "{\"reason\":\"x\"}", USER, STOREKEEPER),
                ep("POST", "/api/v1/requests/" + ID + "/review", null, STOREKEEPER),
                ep("POST", "/api/v1/requests/" + ID + "/reject", "{\"reason\":\"x\"}", STOREKEEPER),
                ep("POST", "/api/v1/requests/" + ID + "/reserve", null, STOREKEEPER),
                ep("POST", "/api/v1/requests/" + ID + "/close", null, STOREKEEPER),
                ep("POST", "/api/v1/requests/" + ID + "/ready-for-issue", null, STOREKEEPER),
                ep("POST", "/api/v1/requests/" + ID + "/issue", null, STOREKEEPER),
                get("/actuator/metrics", ADMIN)
        );
    }
}
