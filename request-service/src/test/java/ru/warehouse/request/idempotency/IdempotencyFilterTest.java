package ru.warehouse.request.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyFilterTest {

    /** Хранилище в памяти: те же правила владения, что в JDBC-версии. */
    static class MemoryStore extends IdempotencyStore {
        record Row(String fp, String status, Integer code, String body, UUID owner) {}
        final Map<String, Row> rows = new HashMap<>();
        MemoryStore() { super(null); }
        private static String k(UUID u, String key) { return u + "|" + key; }
        @Override boolean claim(UUID u, String key, String fp, UUID owner) {
            return rows.putIfAbsent(k(u, key), new Row(fp, "IN_PROGRESS", null, null, owner)) == null;
        }
        @Override Optional<Entry> find(UUID u, String key) {
            Row r = rows.get(k(u, key));
            return r == null ? Optional.empty() : Optional.of(new Entry(r.fp, r.status, r.code, r.body, null));
        }
        @Override boolean takeOver(UUID u, String key, Duration t, UUID owner) { return false; }
        @Override boolean touch(UUID u, String key, UUID owner) { return true; } // «сердцебиение»: в памяти ничего не нужно
        @Override boolean complete(UUID u, String key, UUID owner, int status, String body) {
            Row r = rows.get(k(u, key));
            if (r == null || !r.owner.equals(owner)) return false;
            rows.put(k(u, key), new Row(r.fp, "COMPLETED", status, body, owner));
            return true;
        }
        @Override void release(UUID u, String key, UUID owner) {
            Row r = rows.get(k(u, key));
            if (r != null && r.owner.equals(owner) && "IN_PROGRESS".equals(r.status)) rows.remove(k(u, key));
        }
    }

    private final MemoryStore store = new MemoryStore();
    private final IdempotencyFilter filter = new IdempotencyFilter(store,
            new IdempotencyProperties(List.of("/api/v1/needs-key"), null, null), JsonMapper.builder().build());
    private final AtomicInteger executions = new AtomicInteger();

    @BeforeEach
    void login() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(UUID.randomUUID().toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
        filter.destroy(); // останавливает поток «сердцебиения»
    }

    private MockHttpServletResponse post(String uri, String key, String body, int answer) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRequestURI(uri);
        if (key != null) req.addHeader("Idempotency-Key", key);
        req.setContent(body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (rq, rs) -> {
            executions.incrementAndGet();
            ((HttpServletResponse) rs).setStatus(answer);
            rs.getWriter().write("{\"n\":" + executions.get() + "}");
        };
        filter.doFilter(req, res, chain);
        return res;
    }

    @Test
    void repeatWithSameKeyAndBody_replaysStoredResponse() throws Exception {
        MockHttpServletResponse first = post("/api/v1/requests", "key-12345678", "{\"a\":1}", 201);
        MockHttpServletResponse second = post("/api/v1/requests", "key-12345678", "{\"a\":1}", 201);
        assertThat(executions).hasValue(1);
        assertThat(second.getStatus()).isEqualTo(201);
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(second.getHeader("Idempotency-Replayed")).isEqualTo("true");
    }

    @Test
    void sameKeyDifferentBody_isRejected() throws Exception {
        post("/api/v1/requests", "key-12345678", "{\"a\":1}", 201);
        MockHttpServletResponse second = post("/api/v1/requests", "key-12345678", "{\"a\":2}", 201);
        assertThat(second.getStatus()).isEqualTo(422);
        assertThat(executions).hasValue(1);
    }

    @Test
    void failedRequest_releasesKey_soRetryExecutes() throws Exception {
        post("/api/v1/requests", "key-12345678", "{}", 409);
        post("/api/v1/requests", "key-12345678", "{}", 201);
        assertThat(executions).hasValue(2);
    }

    @Test
    void requiredPath_withoutKey_is400_otherPathsPassThrough() throws Exception {
        assertThat(post("/api/v1/needs-key", null, "{}", 201).getStatus()).isEqualTo(400);
        assertThat(executions).hasValue(0);
        assertThat(post("/api/v1/requests", null, "{}", 201).getStatus()).isEqualTo(201);
        assertThat(executions).hasValue(1);
    }

    /** M3: тот же JSON в другой записи (порядок полей, форма чисел) считается тем же запросом. */
    @Test
    void sameJsonInDifferentFormat_isReplayed() throws Exception {
        post("/api/v1/requests", "key-12345678", "{\"a\":1,\"b\":2.0}", 201);
        MockHttpServletResponse second = post("/api/v1/requests", "key-12345678", "{\"b\":2,\"a\":1.00}", 201);
        assertThat(executions).hasValue(1);
        assertThat(second.getHeader("Idempotency-Replayed")).isEqualTo("true");
    }

    @Test
    void invalidKey_is400() throws Exception {
        assertThat(post("/api/v1/requests", "short", "{}", 201).getStatus()).isEqualTo(400);
    }
}
