// Remote.java
package ru.warehouse.inventory.client;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import ru.warehouse.common.exception.ApiException;
import java.util.function.Supplier;

/** Единый перевод ошибок соседних сервисов в ошибки нашего API. */
final class Remote {
    private Remote() {}

    static <T> T call(String service, Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw ApiException.validation("Сервис " + service + " отклонил запрос (HTTP "
                        + e.getStatusCode().value() + ")");
            }
            throw ApiException.upstream("Сервис " + service + " вернул ошибку (HTTP " + e.getStatusCode().value() + ")");
        } catch (ResourceAccessException e) {
            throw ApiException.upstream("Сервис " + service + " недоступен");
        }
    }
}