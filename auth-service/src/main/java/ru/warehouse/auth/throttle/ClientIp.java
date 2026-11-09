package ru.warehouse.auth.throttle;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

/**
 * Определяет IP клиента для защиты от перебора (M6).
 * <p>
 * auth-service слушает только 127.0.0.1, поэтому запрос приходит от gateway. Только в этом случае берётся адрес
 * из X-Forwarded-For, и считается он <b>справа</b>: каждый доверенный прокси дописывает в конец адрес того, от кого
 * получил запрос, а всё левее мог подставить клиент. Число доверенных прокси между клиентом и auth-service
 * ({@code trustedHops}) задаёт {@code app.security.trusted-proxy-hops}:
 * <ul>
 *   <li>1: только gateway (клиент приходит прямо на gateway). Адрес клиента: последний элемент;</li>
 *   <li>2: nginx перед gateway. Список «клиент, nginx», адрес клиента: предпоследний элемент.</li>
 * </ul>
 * Если запрос пришёл не с loopback, заголовку не доверяем. Если элементов меньше, чем доверенных прокси
 * (неверная настройка), берётся самый левый доступный.
 */
public final class ClientIp {
    private static final int MAX_LENGTH = 64;

    private ClientIp() {}

    /** Один доверенный прокси (gateway): прежнее поведение. */
    public static String resolve(HttpServletRequest request) {
        return resolve(request, 1);
    }

    public static String resolve(HttpServletRequest request, int trustedHops) {
        String remote = request.getRemoteAddr();
        if (isLoopback(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                List<String> parts = Arrays.stream(forwarded.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
                if (!parts.isEmpty()) {
                    int index = Math.max(0, parts.size() - Math.max(1, trustedHops));
                    return truncate(parts.get(index));
                }
            }
        }
        return truncate(remote == null || remote.isBlank() ? "unknown" : remote);
    }

    private static boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "::1".equals(address) || "0:0:0:0:0:0:0:1".equals(address);
    }

    private static String truncate(String value) {
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    }
}
