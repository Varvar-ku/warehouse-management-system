package ru.warehouse.auth.throttle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Раз в час удаляет записи, по которым давно не было неудачных попыток и нет активной блокировки. */
@Slf4j
@Component
class LoginThrottleCleanup {
    private final LoginThrottleService throttle;

    LoginThrottleCleanup(LoginThrottleService throttle) {
        this.throttle = throttle;
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    void cleanup() {
        int deleted = throttle.deleteStale();
        if (deleted > 0) {
            log.debug("login_throttle: удалено устаревших записей: {}", deleted);
        }
    }
}
