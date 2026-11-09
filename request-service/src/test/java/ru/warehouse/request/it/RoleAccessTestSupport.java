package ru.warehouse.request.it;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Матрица доступа: для каждого эндпоинта и каждой роли проверяется, что разрешённая роль не получает 401 и 403, а
 * остальные получают 403; без токена, с мусором, просроченным токеном, чужой подписью и чужим издателем: 401.
 * Тело запроса для эндпоинтов с @Valid должно быть корректным: иначе ответ 400 придёт раньше проверки прав.
 */
abstract class RoleAccessTestSupport extends IntegrationTestBase {
    static final String USER = "USER";
    static final String STOREKEEPER = "STOREKEEPER";
    static final String WORKER = "WAREHOUSE_WORKER";
    static final String ADMIN = "ADMIN";
    /** Токен с ролью SERVICE и именем request-service (так ходит request-service). */
    static final String SERVICE = "SERVICE";
    static final String[] ALL_ROLES = {USER, STOREKEEPER, WORKER, ADMIN, SERVICE};

    static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID ID2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    record Endpoint(String method, String path, String body, Set<String> allowed) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    static Endpoint ep(String method, String path, String body, String... allowedRoles) {
        return new Endpoint(method, path, body, Set.of(allowedRoles));
    }

    static Endpoint get(String path, String... roles) {
        return ep("GET", path, null, roles);
    }

    abstract List<Endpoint> endpoints();

    /** Статус и начало тела ответа: код ошибки и пояснение показывают, почему получен именно этот статус. */
    record Result(int status, String body) {
        String shortBody() {
            String b = body == null ? "" : body.replaceAll("\\s+", " ");
            return b.length() > 700 ? b.substring(0, 700) + "..." : b;
        }
    }

    private Result call(Endpoint e, String token) {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(HttpMethod.valueOf(e.method()), e.path());
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (e.body() != null) request.contentType(MediaType.APPLICATION_JSON).content(e.body());
        try {
            org.springframework.test.web.servlet.MvcResult mvcResult = mvc.perform(request).andReturn();
            MockHttpServletResponse response = mvcResult.getResponse();
            Exception resolved = mvcResult.getResolvedException(); // причина 400/500, которую обработчик ошибок скрывает от клиента
            return new Result(response.getStatus(), response.getContentAsString()
                    + (resolved == null ? "" : " [exception: " + resolved + "]"));
        } catch (Exception ex) {
            return new Result(500, ex.toString()); // права пройдены, упало бизнес-действие: для этой проверки это допустимо
        }
    }

    private int status(Endpoint e, String token) {
        return call(e, token).status();
    }

    private String tokenFor(String role) {
        return SERVICE.equals(role) ? TestTokens.service(UUID.randomUUID(), "request-service")
                : TestTokens.user(UUID.randomUUID(), role);
    }

    @TestFactory
    Stream<DynamicTest> rolesAreEnforced() {
        return endpoints().stream().map(e -> DynamicTest.dynamicTest(e.toString(), () -> {
            List<String> violations = new ArrayList<>();
            for (String role : ALL_ROLES) {
                Result result = call(e, tokenFor(role));
                int status = result.status();
                boolean allowed = e.allowed().contains(role);
                if (allowed && (status == 401 || status == 403)) {
                    violations.add(role + " должна иметь доступ, получила " + status + ": " + result.shortBody());
                }
                if (!allowed && status != 403) {
                    violations.add(role + " не должна иметь доступ (ожидался 403), получила " + status
                            + ": " + result.shortBody());
                }
            }
            assertThat(violations).as(e.toString()).isEmpty();
        }));
    }

    @TestFactory
    Stream<DynamicTest> invalidTokensAreRejectedWith401() {
        return endpoints().stream().map(e -> DynamicTest.dynamicTest(e.toString(), () -> {
            List<String> violations = new ArrayList<>();
            check(violations, "без токена", status(e, null));
            check(violations, "мусор вместо токена", status(e, "not-a-jwt"));
            check(violations, "просроченный токен", status(e, TestTokens.expired()));
            check(violations, "чужая подпись", status(e, TestTokens.wrongSignature()));
            check(violations, "чужой издатель", status(e, TestTokens.wrongIssuer()));
            assertThat(violations).as(e.toString()).isEmpty();
        }));
    }

    private static void check(List<String> violations, String what, int status) {
        if (status != 401) violations.add(what + ": ожидался 401, получен " + status);
    }
}
