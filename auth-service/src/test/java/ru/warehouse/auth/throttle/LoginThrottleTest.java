package ru.warehouse.auth.throttle;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {
    private static final Duration BASE = Duration.ofSeconds(30);
    private static final Duration MAX = Duration.ofMinutes(15);

    @Test
    void noLockBeforeThreshold() {
        assertThat(LoginThrottleService.lockSeconds(1, 5, BASE, MAX)).isZero();
        assertThat(LoginThrottleService.lockSeconds(4, 5, BASE, MAX)).isZero();
    }

    @Test
    void lockDoublesAfterThreshold() {
        assertThat(LoginThrottleService.lockSeconds(5, 5, BASE, MAX)).isEqualTo(30);
        assertThat(LoginThrottleService.lockSeconds(6, 5, BASE, MAX)).isEqualTo(60);
        assertThat(LoginThrottleService.lockSeconds(7, 5, BASE, MAX)).isEqualTo(120);
    }

    @Test
    void lockIsCapped() {
        assertThat(LoginThrottleService.lockSeconds(30, 5, BASE, MAX)).isEqualTo(900);
        assertThat(LoginThrottleService.lockSeconds(1_000_000, 5, BASE, MAX)).isEqualTo(900);
    }

    @Test
    void keysAreBuiltFromLoginAndIp() {
        var service = new LoginThrottleService(new LoginThrottleProperties(true, 5, BASE, 30, BASE, MAX, MAX));
        var keys = service.keys("admin", "10.0.0.5");
        assertThat(keys.pair()).isEqualTo("p:admin|10.0.0.5");
        assertThat(keys.ip()).isEqualTo("i:10.0.0.5");
    }

    @Test
    void clientIpHeaderIsUsedOnlyFromLoopback_andForwardedForIsIgnored() {
        MockHttpServletRequest fromGateway = new MockHttpServletRequest();
        fromGateway.setRemoteAddr("127.0.0.1");
        fromGateway.addHeader(ClientIp.HEADER, "198.51.100.7"); // ставит только gateway
        fromGateway.addHeader("X-Forwarded-For", "9.9.9.9");
        assertThat(ClientIp.resolve(fromGateway)).isEqualTo("198.51.100.7");

        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("203.0.113.5");
        direct.addHeader(ClientIp.HEADER, "9.9.9.9");
        assertThat(ClientIp.resolve(direct)).isEqualTo("203.0.113.5");

        MockHttpServletRequest noHeader = new MockHttpServletRequest();
        noHeader.setRemoteAddr("127.0.0.1");
        noHeader.addHeader("X-Forwarded-For", "9.9.9.9");
        assertThat(ClientIp.resolve(noHeader)).isEqualTo("127.0.0.1");
    }
}
