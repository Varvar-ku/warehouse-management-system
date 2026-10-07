package ru.warehouse.inventory.idempotency;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IdempotencyFilterTest {
    private static final String KEY = "key-0123456789";

    private final IdempotencyStore store = mock(IdempotencyStore.class);
    private final IdempotencyFilter filter = new IdempotencyFilter(store, new IdempotencyProperties(null, null, null));
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void authenticate() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(userId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/v1/issues/1/confirm");
        r.addHeader("Idempotency-Key", KEY);
        r.setContentType("application/json");
        r.setContent("{}".getBytes(StandardCharsets.UTF_8));
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
}