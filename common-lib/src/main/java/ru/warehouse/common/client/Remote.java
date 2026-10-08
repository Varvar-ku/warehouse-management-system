package ru.warehouse.common.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import ru.warehouse.common.exception.ApiError;
import ru.warehouse.common.exception.ApiException;
import java.util.function.Supplier;

/** Единый перевод ошибок соседних сервисов: 4xx передаём как есть (код, пояснения), 5xx и недоступность → 502. */
public final class Remote {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Remote() {}

    public static <T> T call(String service, Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (e.getStatusCode().is4xxClientError()) {
                ApiError error = parse(e.getResponseBodyAsString());
                HttpStatus status = HttpStatus.resolve(code);
                if (error != null && error.code() != null && status != null) {
                    throw ApiException.passthrough(status, error.code(), error.message(), error.details());
                }
                throw ApiException.validation("Сервис " + service + " отклонил запрос (HTTP " + code + ")");
            }
            throw ApiException.upstream("Сервис " + service + " вернул ошибку (HTTP " + code + ")");
        } catch (ResourceAccessException e) {
            throw ApiException.upstream("Сервис " + service + " недоступен");
        }
    }

    private static ApiError parse(String body) {
        try {
            return body == null || body.isBlank() ? null : MAPPER.readValue(body, ApiError.class);
        } catch (JacksonException e) {
            return null;
        }
    }
}
