package ru.warehouse.request.it;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * M3: идемпотентность в request-service. Проверяется сам фильтр на тестовом эндпоинте со счётчиком вызовов
 * (доменные DTO не нужны): повтор, параллельные запросы, другой запрос с тем же ключом, канонический JSON,
 * освобождение ключа после ошибки, «сердцебиение» долгого запроса, перехват брошенной записи.
 * Таймаут перехвата уменьшен до 1,5 с, чтобы тесты были быстрыми.
 */
@TestPropertySource(properties = {
        "app.idempotency.required-paths=/api/v1/test-idempotency/required",
        "app.idempotency.in-progress-timeout=1500ms"})
class IdempotencyIntegrationTest extends IntegrationTestBase {
    private static final String PATH = "/api/v1/test-idempotency";
    private static final AtomicInteger CALLS = new AtomicInteger();

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        Probe idempotencyProbe() {
            return new Probe();
        }
    }

    @RestController
    @RequestMapping(PATH)
    static class Probe {
        @PostMapping
        ResponseEntity<Map<String, Object>> post(@RequestBody Map<String, Object> body) throws InterruptedException {
            int n = CALLS.incrementAndGet();
            if (body.get("sleepMs") instanceof Number s) Thread.sleep(s.longValue());
            if (Boolean.TRUE.equals(body.get("fail"))) {
                return ResponseEntity.badRequest().body(Map.of("code", "PROBE_FAILED"));
            }
            return ResponseEntity.ok(Map.of("call", n));
        }

        @PostMapping("/required")
        Map<String, Object> required(@RequestBody Map<String, Object> body) {
            return Map.of("call", CALLS.incrementAndGet());
        }
    }

    private MockMvc idem;
    private final String userToken = TestTokens.user(UUID.randomUUID(), "USER");

    @BeforeEach
    void buildIdempotentMockMvc() {
        CALLS.set(0);
        // порядок как в работе: сначала Spring Security, затем фильтр идемпотентности
        idem = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class),
                        context.getBean("idempotencyFilter", Filter.class))
                .build();
    }

    private MockHttpServletResponse send(String token, String key, String path, String body) throws Exception {
        var request = post(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) request = request.header("Idempotency-Key", key);
        return idem.perform(request).andReturn().getResponse();
    }

    private static String newKey() {
        return "test-" + UUID.randomUUID();
    }

    @Test
    void repeatWithSameKeyAndBody_executesOnce_andReplaysResponse() throws Exception {
        String key = newKey();
        MockHttpServletResponse first = send(userToken, key, PATH, "{\"x\":1}");
        MockHttpServletResponse second = send(userToken, key, PATH, "{\"x\":1}");

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(second.getHeader("Idempotency-Replayed")).isEqualTo("true");
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(CALLS.get()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBody_isRejectedWith422() throws Exception {
        String key = newKey();
        send(userToken, key, PATH, "{\"x\":1}");
        MockHttpServletResponse other = send(userToken, key, PATH, "{\"x\":2}");

        assertThat(other.getStatus()).isEqualTo(422);
        assertThat(other.getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(CALLS.get()).isEqualTo(1);
    }

    @Test
    void sameJsonInDifferentFormat_isTreatedAsSameRequest() throws Exception {
        String key = newKey();
        send(userToken, key, PATH, "{\"a\":1,\"b\":2.0,\"c\":[1,{\"y\":1,\"x\":2}]}");
        MockHttpServletResponse second = send(userToken, key, PATH, "{\"c\":[1,{\"x\":2,\"y\":1}],\"b\":2,\"a\":1.00}");

        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(second.getHeader("Idempotency-Replayed")).isEqualTo("true");
        assertThat(CALLS.get()).isEqualTo(1);
    }

    @Test
    void sameKeyFromDifferentUsers_areIndependent() throws Exception {
        String key = newKey();
        String otherUser = TestTokens.user(UUID.randomUUID(), "USER");
        MockHttpServletResponse a = send(userToken, key, PATH, "{\"x\":1}");
        MockHttpServletResponse b = send(otherUser, key, PATH, "{\"x\":1}");

        assertThat(a.getStatus()).isEqualTo(200);
        assertThat(b.getStatus()).isEqualTo(200);
        assertThat(b.getHeader("Idempotency-Replayed")).isNull();
        assertThat(CALLS.get()).isEqualTo(2);
    }

    @Test
    void failedRequest_releasesKey_soRetryExecutesAgain() throws Exception {
        String key = newKey();
        MockHttpServletResponse failed = send(userToken, key, PATH, "{\"fail\":true}");
        MockHttpServletResponse retry = send(userToken, key, PATH, "{\"fail\":true}");

        assertThat(failed.getStatus()).isEqualTo(400);
        assertThat(retry.getStatus()).isEqualTo(400);
        assertThat(retry.getHeader("Idempotency-Replayed")).isNull();
        assertThat(CALLS.get()).isEqualTo(2); // ошибки не кэшируются
    }

    @Test
    void parallelRequestsWithSameKey_executeExactlyOnce() throws Exception {
        String key = newKey();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<MockHttpServletResponse> task = () -> {
                    gate.await();
                    return send(userToken, key, PATH, "{\"sleepMs\":400}");
                };
                futures.add(pool.submit(task));
            }
            gate.countDown();
            int executed = 0;
            for (Future<MockHttpServletResponse> f : futures) {
                MockHttpServletResponse r = f.get();
                assertThat(r.getStatus()).isIn(200, 409); // 409: первый ещё выполняется, 200: повтор после завершения
                if (r.getStatus() == 200 && r.getHeader("Idempotency-Replayed") == null) executed++;
                if (r.getStatus() == 409) assertThat(r.getContentAsString()).contains("REQUEST_IN_PROGRESS");
            }
            assertThat(executed).isEqualTo(1);
            assertThat(CALLS.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void longRunningRequest_isNotTakenOver_whileItIsAlive() throws Exception {
        String key = newKey();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // выполняется 3,5 с при таймауте перехвата 1,5 с: без «сердцебиения» второй запрос перехватил бы ключ
            Future<MockHttpServletResponse> first = pool.submit(() -> send(userToken, key, PATH, "{\"sleepMs\":3500}"));
            Thread.sleep(2500);
            MockHttpServletResponse second = send(userToken, key, PATH, "{\"sleepMs\":3500}");

            assertThat(second.getStatus()).isEqualTo(409);
            assertThat(second.getContentAsString()).contains("REQUEST_IN_PROGRESS");
            assertThat(first.get().getStatus()).isEqualTo(200);
            assertThat(CALLS.get()).isEqualTo(1);

            MockHttpServletResponse replay = send(userToken, key, PATH, "{\"sleepMs\":3500}");
            assertThat(replay.getHeader("Idempotency-Replayed")).isEqualTo("true");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void abandonedRecord_isTakenOver_afterTimeout() throws Exception {
        String key = newKey();
        send(userToken, key, PATH, "{\"x\":1}");
        // имитация падения процесса: запрос выполнился, а ответ не сохранён и «сердцебиения» давно нет
        jdbc.update("UPDATE idempotency_keys SET status = 'IN_PROGRESS', response_status = NULL, response_body = NULL, "
                + "completed_at = NULL, locked_at = now() - interval '1 hour' WHERE idem_key = ?", key);

        MockHttpServletResponse retry = send(userToken, key, PATH, "{\"x\":1}");

        assertThat(retry.getStatus()).isEqualTo(200);
        assertThat(retry.getHeader("Idempotency-Replayed")).isNull();
        assertThat(CALLS.get()).isEqualTo(2); // известное ограничение: брошенный запрос выполняется повторно
    }

    @Test
    void requiredPath_withoutKey_isRejected_andWorksWithKey() throws Exception {
        MockHttpServletResponse without = send(userToken, null, PATH + "/required", "{}");
        assertThat(without.getStatus()).isEqualTo(400);
        assertThat(without.getContentAsString()).contains("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(CALLS.get()).isZero();

        MockHttpServletResponse with = send(userToken, newKey(), PATH + "/required", "{}");
        assertThat(with.getStatus()).isEqualTo(200);
    }

    @Test
    void malformedKey_isRejected() throws Exception {
        MockHttpServletResponse r = send(userToken, "bad key!", PATH, "{}");
        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(r.getContentAsString()).contains("IDEMPOTENCY_KEY_INVALID");
    }
}
