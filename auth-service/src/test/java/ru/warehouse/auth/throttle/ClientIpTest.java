package ru.warehouse.auth.throttle;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {

    private MockHttpServletRequest request(String remote, String clientIpHeader, String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        if (clientIpHeader != null) r.addHeader(ClientIp.HEADER, clientIpHeader);
        if (xff != null) r.addHeader("X-Forwarded-For", xff);
        return r;
    }

    @Test
    void usesHeaderFromLoopback() {
        assertThat(ClientIp.resolve(request("127.0.0.1", "203.0.113.7", null))).isEqualTo("203.0.113.7");
        assertThat(ClientIp.resolve(request("::1", "2001:db8::1", null))).isEqualTo("2001:db8::1");
    }

    @Test
    void ignoresXForwardedFor() {
        assertThat(ClientIp.resolve(request("127.0.0.1", null, "1.2.3.4"))).isEqualTo("127.0.0.1");
    }

    @Test
    void ignoresHeaderNotFromLoopback() {
        assertThat(ClientIp.resolve(request("10.0.0.5", "203.0.113.7", null))).isEqualTo("10.0.0.5");
    }

    @Test
    void ignoresGarbageHeader() {
        assertThat(ClientIp.resolve(request("127.0.0.1", "evil'; DROP", null))).isEqualTo("127.0.0.1");
        assertThat(ClientIp.resolve(request("127.0.0.1", "x".repeat(200), null))).isEqualTo("127.0.0.1");
    }
}
