package ru.warehouse.auth.throttle;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {
    private static MockHttpServletRequest request(String remote, String forwarded) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        if (forwarded != null) r.addHeader("X-Forwarded-For", forwarded);
        return r;
    }

    @Test
    void singleProxy_takesLastElement_ignoringClientSpoofedPrefix() {
        assertThat(ClientIp.resolve(request("127.0.0.1", "6.6.6.6, 203.0.113.7"), 1)).isEqualTo("203.0.113.7");
    }

    @Test
    void twoProxies_takeSecondFromEnd() {
        assertThat(ClientIp.resolve(request("127.0.0.1", "6.6.6.6, 203.0.113.7, 10.0.0.2"), 2)).isEqualTo("203.0.113.7");
    }

    @Test
    void headerFromNonLoopback_isIgnored() {
        assertThat(ClientIp.resolve(request("198.51.100.9", "1.2.3.4"), 1)).isEqualTo("198.51.100.9");
    }

    @Test
    void fewerElementsThanTrustedHops_takesLeftmostAvailable() {
        assertThat(ClientIp.resolve(request("127.0.0.1", "203.0.113.7"), 3)).isEqualTo("203.0.113.7");
    }

    @Test
    void noHeader_usesRemoteAddress() {
        assertThat(ClientIp.resolve(request("127.0.0.1", null))).isEqualTo("127.0.0.1");
    }

    @Test
    void ipv6Loopback_isTrusted() {
        assertThat(ClientIp.resolve(request("::1", "203.0.113.7"), 1)).isEqualTo("203.0.113.7");
    }

    @Test
    void hopsBelowOne_areTreatedAsOne() {
        assertThat(ClientIp.resolve(request("127.0.0.1", "6.6.6.6, 203.0.113.7"), 0)).isEqualTo("203.0.113.7");
    }

    @Test
    void longValue_isTruncated() {
        String longValue = "a".repeat(200);
        assertThat(ClientIp.resolve(request("127.0.0.1", longValue), 1)).hasSize(64);
    }
}
