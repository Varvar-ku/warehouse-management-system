package ru.warehouse.warehouse.it;

import java.util.List;

/** Коды 401 и 403 по ролям для эндпоинтов warehouse-service. */
class RoleAccessIntegrationTest extends RoleAccessTestSupport {
    private static final String WAREHOUSE = "\"00000000-0000-0000-0000-0000000000a1\"";
    private static final String PRODUCT = "\"00000000-0000-0000-0000-0000000000b1\"";
    private static final String SECTION = "\"" + ID + "\"";

    @Override
    List<Endpoint> endpoints() {
        return List.of(
                get("/api/v1/warehouses", ALL_ROLES),
                get("/api/v1/warehouses/" + ID, ALL_ROLES),
                ep("POST", "/api/v1/warehouses", "{\"code\":\"T-1\",\"name\":\"n\"}", ADMIN),
                ep("PATCH", "/api/v1/warehouses/" + ID, "{}", ADMIN),
                get("/api/v1/sections", STOREKEEPER, ADMIN, WORKER),
                get("/api/v1/sections/" + ID, STOREKEEPER, ADMIN, WORKER),
                ep("POST", "/api/v1/sections", "{\"warehouseId\":\"00000000-0000-0000-0000-0000000000a1\",\"code\":\"T-1\",\"name\":\"n\"}", ADMIN),
                ep("PATCH", "/api/v1/sections/" + ID, "{}", ADMIN),
                get("/api/v1/placement-rules", STOREKEEPER, ADMIN),
                get("/api/v1/placement-rules/" + ID, STOREKEEPER, ADMIN),
                get("/api/v1/placement-rules/" + ID + "/versions", STOREKEEPER, ADMIN),
                ep("PATCH", "/api/v1/placement-rules/" + ID, "{}", ADMIN),
                get("/actuator/metrics", ADMIN)
        );
    }
}
