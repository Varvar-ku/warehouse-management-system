// ClientConfig.java
package ru.warehouse.inventory.client;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.web.TraceIdFilter;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
class ClientConfig {

    @Bean
    RestClient catalogRestClient(ServicesProperties props) {
        return build(props.catalogUrl());
    }

    @Bean
    RestClient warehouseRestClient(ServicesProperties props) {
        return build(props.warehouseUrl());
    }

    private static RestClient build(String baseUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(propagateContext())
                .build();
    }

    /** Пересылает JWT текущего пользователя и trace-id: соседний сервис сам проверит права. */
    private static ClientHttpRequestInterceptor propagateContext() {
        return (request, body, execution) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwt) {
                request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
            }
            String traceId = MDC.get(TraceIdFilter.MDC_KEY);
            if (traceId != null) {
                request.getHeaders().set(TraceIdFilter.HEADER, traceId);
            }
            return execution.execute(request, body);
        };
    }
}