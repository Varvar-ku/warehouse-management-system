package ru.warehouse.warehouse.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.Remote;
import ru.warehouse.common.web.TraceIdFilter;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Справочники catalog-service для проверки ограничений секции (M5). Вызывается до открытия транзакции;
 * JWT администратора, создающего секцию, пересылается дальше (чтение справочников доступно любой роли).
 */
@Component
class CatalogReferences {
    private static final String SERVICE = "catalog-service";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CodeItem(String code) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Definition(String code, Set<String> options) {}

    private final RestClient client;

    CatalogReferences(@Value("${app.services.catalog-url:http://localhost:8082}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
                        request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
                    }
                    String traceId = MDC.get(TraceIdFilter.MDC_KEY);
                    if (traceId != null) request.getHeaders().set("X-Trace-Id", traceId);
                    return execution.execute(request, body);
                }).build();
    }

    Set<String> unitCodes() {
        return codes("/api/v1/units");
    }

    Set<String> productTypeCodes() {
        return codes("/api/v1/product-types");
    }

    /** Допустимые материалы: значения справочного атрибута MATERIAL; пустое множество, если определения нет. */
    Set<String> materialCodes() {
        List<Definition> definitions = Remote.call(SERVICE, () -> client.get().uri("/api/v1/attribute-definitions")
                .retrieve().body(new ParameterizedTypeReference<List<Definition>>() {}));
        if (definitions == null) return Set.of();
        return definitions.stream().filter(d -> "MATERIAL".equalsIgnoreCase(d.code()) && d.options() != null)
                .flatMap(d -> d.options().stream()).map(CatalogReferences::normalize).collect(Collectors.toSet());
    }

    private Set<String> codes(String path) {
        List<CodeItem> items = Remote.call(SERVICE, () -> client.get().uri(path).retrieve()
                .body(new ParameterizedTypeReference<List<CodeItem>>() {}));
        return items == null ? Set.of() : items.stream().map(i -> normalize(i.code())).collect(Collectors.toSet());
    }

    static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
