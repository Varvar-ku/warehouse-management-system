package ru.warehouse.warehouse.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.Remote;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.common.web.TraceIdFilter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * M4: склад нельзя отключить, пока в нём есть остатки, открытые документы inventory-service и открытые заявки.
 * Проверка идёт до транзакции изменения склада; JWT администратора пересылается соседям.
 */
@Component
public class WarehouseUsageGuard {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record InventoryUsage(BigDecimal stockQuantity, long openReceipts, long draftWriteOffs,
                          long draftIssues, long draftAdjustments) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenRequests(long openRequests) {}

    private final RestClient inventory;
    private final RestClient requests;

    public WarehouseUsageGuard(@Value("${app.services.inventory-url:http://localhost:8084}") String inventoryUrl,
                               @Value("${app.services.request-url:http://localhost:8085}") String requestUrl) {
        this.inventory = build(inventoryUrl);
        this.requests = build(requestUrl);
    }

    public void assertCanDeactivate(UUID warehouseId) {
        InventoryUsage inv = Remote.call("inventory-service", () -> inventory.get()
                .uri("/api/v1/stock/warehouses/{id}/usage", warehouseId).retrieve().body(InventoryUsage.class));
        OpenRequests open = Remote.call("request-service", () -> requests.get()
                .uri("/api/v1/requests/usage?warehouseId={id}", warehouseId).retrieve().body(OpenRequests.class));
        List<String> reasons = new ArrayList<>();
        if (inv != null) {
            if (inv.stockQuantity() != null && inv.stockQuantity().signum() > 0) {
                reasons.add("остаток на складе: " + inv.stockQuantity().stripTrailingZeros().toPlainString());
            }
            if (inv.openReceipts() > 0) reasons.add("открытых поступлений: " + inv.openReceipts());
            if (inv.draftWriteOffs() > 0) reasons.add("черновиков списаний: " + inv.draftWriteOffs());
            if (inv.draftIssues() > 0) reasons.add("черновиков документов выдачи: " + inv.draftIssues());
            if (inv.draftAdjustments() > 0) reasons.add("черновиков корректировок: " + inv.draftAdjustments());
        }
        if (open != null && open.openRequests() > 0) reasons.add("открытых заявок: " + open.openRequests());
        if (!reasons.isEmpty()) {
            throw ApiException.conflict("WAREHOUSE_IN_USE",
                    "Склад нельзя отключить: " + String.join("; ", reasons), reasons);
        }
    }

    private static RestClient build(String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl(url).requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
                        request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue()); // JWT администратора
                    }
                    String traceId = MDC.get(TraceIdFilter.MDC_KEY);
                    if (traceId != null) request.getHeaders().set("X-Trace-Id", traceId);
                    return execution.execute(request, body);
                }).build();
    }
}
