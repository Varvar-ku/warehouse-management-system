package ru.warehouse.inventory.idempotency;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IdempotencyFilterTest {
    private static final String KEY = "key-0123456789";

    private final IdempotencyStore store = mock(IdempotencyStore.class);
    private final IdempotencyFilter filter = new IdempotencyFilter(store,
            new IdempotencyProperties(null, null, null), JsonMapper.builder().build());
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void authenticate() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(userId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        filter.destroy(); // останавливает поток «сердцебиения»
    }

    private MockHttpServletRequest request() {
        return request("{}");
    }

    private MockHttpServletRequest request(String body) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/v1/issues/1/confirm");
        r.addHeader("Idempotency-Key", KEY);
        r.setContentType("application/json");
        r.setContent(body.getBytes(StandardCharsets.UTF_8));
        return r;
    }

    @Test
    void lostOwnership_neverDeletesTheWinnersRecord() throws Exception {
        when(store.claim(any(), any(), any(), any())).thenReturn(true);
        when(store.complete(any(), any(), any(), anyInt(), any())).thenReturn(false); // ключ уже перехвачен

        filter.doFilter(request(), new MockHttpServletResponse(),
                (req, res) -> ((HttpServletResponse) res).setStatus(201));

        verify(store, never()).release(any(), any(), any());
    }

    @Test
    void failedRequest_releasesOnlyItsOwnKey() throws Exception {
        when(store.claim(any(), any(), any(), any())).thenReturn(true);

        filter.doFilter(request(), new MockHttpServletResponse(),
                (req, res) -> ((HttpServletResponse) res).setStatus(409));

        verify(store).release(eq(userId), eq(KEY), any(UUID.class));
        verify(store, never()).complete(any(), any(), any(), anyInt(), any());
    }

    /** M3: запрос уже выполнен, но ответ сохранить не удалось: ключ не освобождается, клиент получает обычный ответ. */
    @Test
    void completeFailure_afterSuccess_keepsKeyAndReturnsResponse() throws Exception {
        when(store.claim(any(), any(), any(), any())).thenReturn(true);
        when(store.complete(any(), any(), any(), anyInt(), any())).thenThrow(new IllegalStateException("db down"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(), response, (req, res) -> ((HttpServletResponse) res).setStatus(201));

        assertThat(response.getStatus()).isEqualTo(201);
        verify(store, never()).release(any(), any(), any());
    }

    /** M3: один и тот же JSON в другой записи (порядок полей, форма чисел) даёт тот же отпечаток. */
    @Test
    void fingerprint_ignoresFieldOrderAndNumberFormat() throws Exception {
        when(store.claim(any(), any(), any(), any())).thenReturn(true);

        filter.doFilter(request("{\"a\":1,\"b\":2.0,\"c\":[1,{\"y\":1,\"x\":2}]}"),
                new MockHttpServletResponse(), (req, res) -> ((HttpServletResponse) res).setStatus(200));
        filter.doFilter(request("{\"c\":[1,{\"x\":2,\"y\":1}],\"b\":2,\"a\":1.00}"),
                new MockHttpServletResponse(), (req, res) -> ((HttpServletResponse) res).setStatus(200));
        filter.doFilter(request("{\"a\":2}"),
                new MockHttpServletResponse(), (req, res) -> ((HttpServletResponse) res).setStatus(200));

        ArgumentCaptor<String> fingerprints = ArgumentCaptor.forClass(String.class);
        verify(store, times(3)).claim(eq(userId), eq(KEY), fingerprints.capture(), any(UUID.class));
        assertThat(fingerprints.getAllValues().get(0)).isEqualTo(fingerprints.getAllValues().get(1));
        assertThat(fingerprints.getAllValues().get(2)).isNotEqualTo(fingerprints.getAllValues().get(0));
    }
}
