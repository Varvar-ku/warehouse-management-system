package ru.warehouse.auth.throttle;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * Определяет IP клиента для защиты от перебора (M6).
 * <p>
 * auth-service слушает только 127.0.0.1, поэтому запрос приходит от gateway. IP клиента gateway передаёт в
 * отдельном заголовке {@value #HEADER}: он пишется только gateway (входящее значение от клиента затирается),
 * поэтому подделать его снаружи нельзя. Заголовок учитывается только если запрос пришёл с loopback.
 * X-Forwarded-For здесь больше не читается: его формат зависит от настроек прокси и от версии Spring Cloud Gateway.
 * <p>
 * Если заголовка нет или он не похож на IP-адрес, берётся адрес соединения.
 */
public final class ClientIp {
    public static final String HEADER = "X-Client-IP";
    private static final int MAX_LENGTH = 64;
    private static final Pattern IP_LIKE = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private ClientIp() {}

    public static String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (isLoopback(remote)) {
            String header = request.getHeader(HEADER);
            if (header != null && IP_LIKE.matcher(header.trim()).matches()) {
                return header.trim();
            }
        }
        return truncate(remote == null || remote.isBlank() ? "unknown" : remote);
    }

    /** @deprecated число прокси больше не используется: адрес клиента определяет gateway. */
    @Deprecated
    public static String resolve(HttpServletRequest request, int trustedHops) {
        return resolve(request);
    }

    private static boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "::1".equals(address) || "0:0:0:0:0:0:0:1".equals(address);
    }

    private static String truncate(String value) {
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    }
}
