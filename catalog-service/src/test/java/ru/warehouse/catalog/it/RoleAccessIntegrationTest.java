package ru.warehouse.catalog.it;

import java.util.List;

/** Коды 401 и 403 по ролям для эндпоинтов catalog-service. */
class RoleAccessIntegrationTest extends RoleAccessTestSupport {
    private static final String WAREHOUSE = "00000000-0000-0000-0000-0000000000a1";
    private static final String PRODUCT = "00000000-0000-0000-0000-0000000000b1";
    private static final String SECTION = "\"" + ID + "\"";

    @Override
    List<Endpoint> endpoints() {
        return List.of(
                // чтение: любая аутентифицированная роль
                get("/api/v1/products", ALL_ROLES),
                get("/api/v1/products/" + ID, ALL_ROLES),
                get("/api/v1/products/batch?ids=" + ID, ALL_ROLES),
                get("/api/v1/product-types", ALL_ROLES),
                get("/api/v1/units", ALL_ROLES),
                get("/api/v1/attribute-definitions", ALL_ROLES),
                // изменение: только администратор
                ep("POST", "/api/v1/products", "{\"sku\":\"T-1\",\"name\":\"n\",\"productTypeId\":" + SECTION + ",\"unitId\":" + SECTION + "}", ADMIN),
                ep("PATCH", "/api/v1/products/" + ID, "{}", ADMIN),
                ep("POST", "/api/v1/products/" + ID + "/archive", null, ADMIN),
                ep("POST", "/api/v1/products/" + ID + "/restore", null, ADMIN),
                ep("POST", "/api/v1/product-types", "{\"code\":\"T-1\",\"name\":\"n\"}", ADMIN),
                ep("PATCH", "/api/v1/product-types/" + ID, "{}", ADMIN),
                ep("POST", "/api/v1/units", "{\"code\":\"U-1\",\"name\":\"n\"}", ADMIN),
                ep("POST", "/api/v1/attribute-definitions", "{\"code\":\"A-1\",\"name\":\"n\",\"dataType\":\"STRING\"}", ADMIN),
                get("/actuator/metrics", ADMIN)
        );
    }
}
