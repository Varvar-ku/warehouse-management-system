// InventoryClient.java
package ru.warehouse.warehouse.client;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.warehouse.common.exception.ApiException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/**
 * Вопросы warehouse-service к inventory-service. Пересылает JWT пользователя и X-Trace-Id,
 * как остальные межсервисные клиенты. Адрес: app.clients.inventory.base-url.
 */
@Component
public class InventoryClient {

    /** Ответ inventory-service: чем занята секция. inUse = есть остаток или незавершённое размещение. */
    public record SectionUsage(UUID sectionId, boolean hasStock, BigDecimal reservedQuantity,
                               long pendingPlacements, boolean inUse) {}

    private final RestClient client;

    public InventoryClient(@Value("${app.clients.inventory.base-url:http://127.0.0.1:8084}") String baseUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        ClientHttpRequestInterceptor forward = (request, body, execution) -> {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
                request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
            }
            String trace = MDC.get("traceId");
            if (trace != null) request.getHeaders().set("X-Trace-Id", trace);
            return execution.execute(request, body);
        };
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .requestInterceptor(forward).build();
    }

    public SectionUsage sectionUsage(UUID sectionId) {
        try {
            SectionUsage usage = client.get().uri("/api/v1/stock/sections/{id}/usage", sectionId)
                    .retrieve().body(SectionUsage.class);
            if (usage == null) throw ApiException.upstream("inventory-service вернул пустой ответ");
            return usage;
        } catch (RestClientException ex) {
            throw ApiException.upstream("inventory-service недоступен или отклонил запрос: " + ex.getMessage());
        }
    }
}
