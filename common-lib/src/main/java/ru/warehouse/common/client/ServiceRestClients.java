package ru.warehouse.common.client;

import org.slf4j.MDC;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.web.TraceIdFilter;
import java.net.http.HttpClient;
import java.time.Duration;

/** Фабрика REST-клиентов для вызовов между сервисами. */
public final class ServiceRestClients {
    private ServiceRestClients() {}

    /** Клиент, который пересылает JWT текущего пользователя и trace-id: соседний сервис сам проверит права. */
    public static RestClient create(String baseUrl) {
        return build(baseUrl, true);
    }

    /**
     * Клиент для внутренних операций: JWT пользователя НЕ пересылается, вызывающий сам ставит заголовок
     * Authorization (служебный токен, P1-10). Trace-id пересылается.
     */
    public static RestClient createWithoutUserToken(String baseUrl) {
        return build(baseUrl, false);
    }

    private static RestClient build(String baseUrl, boolean forwardUserToken) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(propagateContext(forwardUserToken))
                .build();
    }

    private static ClientHttpRequestInterceptor propagateContext(boolean forwardUserToken) {
        return (request, body, execution) -> {
            if (forwardUserToken) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth instanceof JwtAuthenticationToken jwt) {
                    request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
                }
            }
            String traceId = MDC.get(TraceIdFilter.MDC_KEY);
            if (traceId != null) {
                request.getHeaders().set(TraceIdFilter.HEADER, traceId);
            }
            return execution.execute(request, body);
        };
    }
}
