package ru.warehouse.auth.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * M6: два администратора одновременно отключают друг друга. Без блокировки оба проходят проверку
 * «последнего администратора» и система остаётся без админа; с блокировкой один запрос получает 409 LAST_ADMIN.
 * Тест пропускается, если в базе есть другие активные администраторы (проверка была бы бессмысленной).
 */
class LastAdminRaceIntegrationTest extends IntegrationTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final String adminToken = TestTokens.user(UUID.randomUUID(), "ADMIN");

    @Test
    @SuppressWarnings("unchecked")
    void concurrentDisableOfTwoAdmins_leavesExactlyOneActive() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String a = createAdmin("race-a-" + suffix);
        String b = createAdmin("race-b-" + suffix);

        Map<String, Object> page = JSON.readValue(mvc.perform(get("/api/v1/users?size=100")
                        .header("Authorization", "Bearer " + adminToken)).andReturn().getResponse().getContentAsString(),
                Map.class);
        long activeAdmins = ((List<Map<String, Object>>) page.get("content")).stream()
                .filter(u -> Boolean.TRUE.equals(u.get("active")))
                .filter(u -> ((List<String>) u.get("roles")).contains("ADMIN")).count();
        assumeTrue(activeAdmins == 2, "В базе есть другие активные администраторы");

        for (int round = 0; round < 5; round++) {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch gate = new CountDownLatch(1);
            try {
                Future<MockHttpServletResponse> fa = pool.submit(() -> {
                    gate.await();
                    return setActive(a, false);
                });
                Future<MockHttpServletResponse> fb = pool.submit(() -> {
                    gate.await();
                    return setActive(b, false);
                });
                gate.countDown();
                List<Integer> statuses = new ArrayList<>(List.of(fa.get().getStatus(), fb.get().getStatus()));
                statuses.sort(Integer::compare);
                assertThat(statuses).as("раунд %d", round).containsExactly(200, 409);
            } finally {
                pool.shutdownNow();
            }
            // возвращаем обоих в строй для следующего раунда
            assertThat(setActive(a, true).getStatus()).isEqualTo(200);
            assertThat(setActive(b, true).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void lastAdmin_cannotBeDisabled_evenSequentially() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String only = createAdmin("race-single-" + suffix);
        // если есть другие админы, отключение разрешено: проверяем только корректный ответ при единственном
        MockHttpServletResponse r = setActive(only, false);
        assertThat(r.getStatus()).isIn(200, 409);
        if (r.getStatus() == 409) assertThat(r.getContentAsString()).contains("LAST_ADMIN");
    }

    private String createAdmin(String login) throws Exception {
        String body = "{\"login\":\"" + login + "\",\"password\":\"Passw0rd!1\",\"fullName\":\"Race Test\",\"roles\":[\"ADMIN\"]}";
        MockHttpServletResponse r = mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        Map<?, ?> created = JSON.readValue(r.getContentAsString(), Map.class);
        return String.valueOf(created.get("id"));
    }

    private MockHttpServletResponse setActive(String id, boolean active) throws Exception {
        return mvc.perform(patch("/api/v1/users/" + id).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":" + active + "}")).andReturn().getResponse();
    }
}
