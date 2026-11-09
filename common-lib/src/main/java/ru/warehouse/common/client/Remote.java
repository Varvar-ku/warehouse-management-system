package ru.warehouse.common.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import ru.warehouse.common.exception.ApiError;
import ru.warehouse.common.exception.ApiException;
import java.util.function.Supplier;

/**
 * Единый перевод ошибок соседних сервисов (M10): 4xx передаём как есть (код, пояснения);
 * 401 и 403 соседа не передаём (это сбой служебного токена или настройки, а не ошибка пользователя; клиент по 401
 * разлогинил бы человека), а отдаём как 502; 5xx, недоступность и нечитаемый ответ тоже 502.
 */
public final class Remote {
    private static final Logger log = LoggerFactory.getLogger(Remote.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Remote() {}

    public static <T> T call(String service, Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 401 || code == 403) {
                log.warn("Сервис {} ответил HTTP {}: проверьте служебный токен и роли вызывающего", service, code);
                throw ApiException.upstream("Сервис " + service + " отказал в доступе (HTTP " + code + ")");
            }
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
            log.warn("Сервис {} недоступен: {}", service, e.getMessage());
            throw ApiException.upstream("Сервис " + service + " недоступен");
        } catch (RestClientException e) {
            // например, ответ не разобран в ожидаемый тип
            log.warn("Сервис {} вернул некорректный ответ: {}", service, e.getMessage());
            throw ApiException.upstream("Сервис " + service + " вернул некорректный ответ");
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
