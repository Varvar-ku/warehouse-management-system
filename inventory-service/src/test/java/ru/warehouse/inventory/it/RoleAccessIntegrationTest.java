package ru.warehouse.inventory.it;

import java.util.List;

/** Коды 401 и 403 по ролям для эндпоинтов inventory-service. */
class RoleAccessIntegrationTest extends RoleAccessTestSupport {
    private static final String WAREHOUSE = "\"00000000-0000-0000-0000-0000000000a1\"";
    private static final String PRODUCT   = "\"00000000-0000-0000-0000-0000000000b1\"";
    private static final String SECTION = "\"" + ID + "\"";

    @Override
    List<Endpoint> endpoints() {
        return List.of(
                // поступления: кладовщик
                ep("POST", "/api/v1/receipts", "{\"warehouseId\":" + WAREHOUSE + ",\"supplierName\":\"s\",\"items\":[{\"productId\":" + PRODUCT + ",\"quantity\":1}]}", STOREKEEPER),
                get("/api/v1/receipts", STOREKEEPER),
                get("/api/v1/receipts/" + ID, STOREKEEPER),
                ep("PATCH", "/api/v1/receipts/" + ID, "{}", STOREKEEPER),
                ep("POST", "/api/v1/receipts/" + ID + "/recommend-placement", null, STOREKEEPER),
                ep("POST", "/api/v1/receipts/" + ID + "/confirm", null, STOREKEEPER),
                ep("POST", "/api/v1/receipts/" + ID + "/placement-tasks", null, STOREKEEPER),
                ep("POST", "/api/v1/receipts/" + ID + "/cancel", "{\"reason\":\"x\"}", STOREKEEPER),
                // задания на размещение: кладовщик и работник; подтверждает работник, решает кладовщик
                get("/api/v1/placement-tasks", STOREKEEPER, WORKER),
                get("/api/v1/placement-tasks/" + ID, STOREKEEPER, WORKER),
                ep("POST", "/api/v1/placement-tasks/" + ID + "/items/" + ID2 + "/confirm", "{\"sectionId\":" + SECTION + ",\"quantity\":1}", WORKER),
                ep("POST", "/api/v1/placement-tasks/" + ID + "/items/" + ID2 + "/resolve", "{\"decision\":\"ACCEPT_ACTUAL\",\"comment\":\"c\"}", STOREKEEPER),
                // остатки и журнал: кладовщик, администратор, работник
                get("/api/v1/stock", STOREKEEPER, ADMIN, WORKER),
                get("/api/v1/stock/movements", STOREKEEPER, ADMIN, WORKER),
                get("/api/v1/stock/sections/" + ID + "/usage", STOREKEEPER, ADMIN, WORKER),
                get("/api/v1/stock/products/" + ID + "/usage", STOREKEEPER, ADMIN),
                get("/api/v1/stock/warehouses/" + ID + "/usage", STOREKEEPER, ADMIN),
                // резервы и выдача: чтение кладовщику и службе, запись только request-service (P1-10)
                ep("POST", "/api/v1/reservations/availability", "{\"warehouseId\":" + WAREHOUSE + ",\"items\":[{\"productId\":" + PRODUCT + ",\"quantity\":1}]}", STOREKEEPER, SERVICE),
                get("/api/v1/reservations?requestId=" + ID, STOREKEEPER, SERVICE),
                ep("POST", "/api/v1/reservations", "{\"requestId\":\"" + ID + "\",\"warehouseId\":" + WAREHOUSE + ",\"allowPartial\":false,\"items\":[{\"requestItemId\":\"" + ID2 + "\",\"productId\":" + PRODUCT + ",\"quantity\":1}]}", SERVICE),
                ep("POST", "/api/v1/reservations/release", "{\"requestId\":\"" + ID + "\"}", SERVICE),
                get("/api/v1/issues", STOREKEEPER, SERVICE),
                get("/api/v1/issues/" + ID, STOREKEEPER, SERVICE),
                ep("POST", "/api/v1/issues", "{\"requestId\":\"" + ID + "\",\"warehouseId\":" + WAREHOUSE + ",\"issuedTo\":\"" + ID2 + "\",\"items\":[{\"requestItemId\":\"" + ID2 + "\",\"productId\":" + PRODUCT + ",\"quantity\":1}]}", SERVICE),
                ep("POST", "/api/v1/issues/" + ID + "/confirm", null, SERVICE),
                ep("POST", "/api/v1/issues/" + ID + "/cancel", "{\"reason\":\"x\"}", SERVICE),
                // списания: просмотр кладовщику и администратору, оформляет кладовщик, одобряет администратор
                get("/api/v1/write-offs", STOREKEEPER, ADMIN),
                get("/api/v1/write-offs/" + ID, STOREKEEPER, ADMIN),
                ep("POST", "/api/v1/write-offs", "{\"warehouseId\":" + WAREHOUSE + ",\"reasonCode\":\"DAMAGED\",\"items\":[{\"stockId\":\"" + ID + "\",\"quantity\":1}]}", STOREKEEPER),
                ep("POST", "/api/v1/write-offs/" + ID + "/approve", null, ADMIN),
                ep("POST", "/api/v1/write-offs/" + ID + "/confirm", null, STOREKEEPER),
                ep("POST", "/api/v1/write-offs/" + ID + "/cancel", "{\"reason\":\"x\"}", STOREKEEPER),
                // корректировки (B1)
                get("/api/v1/adjustments", STOREKEEPER, ADMIN),
                get("/api/v1/adjustments/" + ID, STOREKEEPER, ADMIN),
                ep("POST", "/api/v1/adjustments", "{\"warehouseId\":" + WAREHOUSE + ",\"comment\":\"c\",\"items\":[{\"shortageId\":\"" + ID + "\"}]}", STOREKEEPER),
                ep("POST", "/api/v1/adjustments/" + ID + "/approve", null, ADMIN),
                ep("POST", "/api/v1/adjustments/" + ID + "/confirm", null, STOREKEEPER),
                ep("POST", "/api/v1/adjustments/" + ID + "/cancel", "{\"reason\":\"x\"}", STOREKEEPER),
                // недостачи (B1)
                get("/api/v1/shortages", STOREKEEPER, ADMIN),
                get("/api/v1/shortages/" + ID, STOREKEEPER, ADMIN),
                ep("POST", "/api/v1/shortages/" + ID + "/found", "{\"comment\":\"found\"}", STOREKEEPER),
                // служебные эндпоинты actuator: только администратор
                get("/actuator/metrics", ADMIN)
        );
    }
}
