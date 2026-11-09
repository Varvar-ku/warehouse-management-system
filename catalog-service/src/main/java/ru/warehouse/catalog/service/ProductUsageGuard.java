package ru.warehouse.catalog.service;

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
 * M4: номенклатуру нельзя архивировать, пока на неё ссылаются остатки, открытые поступления, активные резервы
 * и открытые заявки (иначе документы и остатки остаются со ссылкой на «исчезнувший» товар). Проверка идёт до
 * транзакции архивации (запросы к соседним сервисам не должны держать её).
 */
@Component
public class ProductUsageGuard {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record InventoryUsage(BigDecimal stockQuantity, long openReceipts, long activeReservations) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenRequests(long openRequests) {}

    private final RestClient inventory;
    private final RestClient requests;

    public ProductUsageGuard(@Value("${app.services.inventory-url:http://localhost:8084}") String inventoryUrl,
                             @Value("${app.services.request-url:http://localhost:8085}") String requestUrl) {
        this.inventory = build(inventoryUrl);
        this.requests = build(requestUrl);
    }

    public void assertArchivable(UUID productId) {
        InventoryUsage inv = Remote.call("inventory-service", () -> inventory.get()
                .uri("/api/v1/stock/products/{id}/usage", productId).retrieve().body(InventoryUsage.class));
        OpenRequests open = Remote.call("request-service", () -> requests.get()
                .uri("/api/v1/requests/usage?productId={id}", productId).retrieve().body(OpenRequests.class));
        List<String> reasons = new ArrayList<>();
        if (inv != null && inv.stockQuantity() != null && inv.stockQuantity().signum() > 0) {
            reasons.add("остаток на складах: " + inv.stockQuantity().stripTrailingZeros().toPlainString());
        }
        if (inv != null && inv.openReceipts() > 0) reasons.add("открытых поступлений: " + inv.openReceipts());
        if (inv != null && inv.activeReservations() > 0) reasons.add("активных резервов: " + inv.activeReservations());
        if (open != null && open.openRequests() > 0) reasons.add("открытых заявок: " + open.openRequests());
        if (!reasons.isEmpty()) {
            throw ApiException.conflict("PRODUCT_IN_USE",
                    "Номенклатуру нельзя архивировать: " + String.join("; ", reasons), reasons);
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
