package ru.warehouse.auth.throttle;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Счётчики неудачных попыток входа в таблице login_throttle.
 * Методы выполняются в транзакции вызывающего кода (AuthService.login с noRollbackFor = ApiException),
 * поэтому счётчик сохраняется, даже когда вход завершается ошибкой 401.
 */
@Service
public class LoginThrottleService {

    /** Ключи счётчиков одной попытки входа. */
    public record Keys(String pair, String ip) {}

    @PersistenceContext
    private EntityManager em;

    private final LoginThrottleProperties props;

    public LoginThrottleService(LoginThrottleProperties props) {
        this.props = props;
    }

    /** login должен быть уже нормализован (trim + lower case). */
    public Keys keys(String login, String clientIp) {
        String l = login.length() > 100 ? login.substring(0, 100) : login;
        String ip = clientIp.length() > 64 ? clientIp.substring(0, 64) : clientIp;
        return new Keys("p:" + l + "|" + ip, "i:" + ip);
    }

    /** Сколько секунд осталось до конца самой долгой из блокировок; 0, если блокировки нет. */
    @Transactional
    public long remainingLockSeconds(Keys keys) {
        if (!props.enabled()) return 0;
        Object result = em.createNativeQuery("""
                SELECT COALESCE(MAX(CEIL(EXTRACT(EPOCH FROM (locked_until - now())))), 0)
                FROM login_throttle
                WHERE throttle_key IN (:pairKey, :ipKey) AND locked_until > now()
                """)
                .setParameter("pairKey", keys.pair())
                .setParameter("ipKey", keys.ip())
                .getSingleResult();
        return ((Number) result).longValue();
    }

    @Transactional
    public void recordFailure(Keys keys) {
        if (!props.enabled()) return;
        registerFailure(keys.pair(), props.pairMaxFailures(), props.pairBaseLock());
        registerFailure(keys.ip(), props.ipMaxFailures(), props.ipBaseLock());
    }

    /** Успешный вход сбрасывает счётчик пары. Счётчик IP не трогаем: он ограничивает перебор разных логинов. */
    @Transactional
    public void recordSuccess(Keys keys) {
        if (!props.enabled()) return;
        em.createNativeQuery("DELETE FROM login_throttle WHERE throttle_key = :key")
                .setParameter("key", keys.pair())
                .executeUpdate();
    }

    @Transactional
    public int deleteStale() {
        return em.createNativeQuery("""
                DELETE FROM login_throttle
                WHERE last_failed_at < now() - (:seconds * interval '1 second')
                  AND (locked_until IS NULL OR locked_until < now())
                """)
                .setParameter("seconds", props.resetAfter().toSeconds())
                .executeUpdate();
    }

    private void registerFailure(String key, int maxFailures, Duration baseLock) {
        // Один атомарный запрос: счётчик растёт или начинается заново, если давно не было неудач
        em.createNativeQuery("""
                INSERT INTO login_throttle (throttle_key, failed_count, last_failed_at, locked_until)
                VALUES (:key, 1, now(), NULL)
                ON CONFLICT (throttle_key) DO UPDATE SET
                    failed_count = CASE
                        WHEN login_throttle.last_failed_at < now() - (:resetSeconds * interval '1 second') THEN 1
                        ELSE login_throttle.failed_count + 1 END,
                    last_failed_at = now()
                """)
                .setParameter("key", key)
                .setParameter("resetSeconds", props.resetAfter().toSeconds())
                .executeUpdate();

        // Строка заблокирована нашей транзакцией до коммита, поэтому значение читаем без гонок
        Number count = (Number) em.createNativeQuery(
                        "SELECT failed_count FROM login_throttle WHERE throttle_key = :key")
                .setParameter("key", key)
                .getSingleResult();

        long lock = lockSeconds(count.intValue(), maxFailures, baseLock, props.maxLock());
        if (lock > 0) {
            em.createNativeQuery("""
                    UPDATE login_throttle SET locked_until = now() + (:lockSeconds * interval '1 second')
                    WHERE throttle_key = :key
                    """)
                    .setParameter("lockSeconds", lock)
                    .setParameter("key", key)
                    .executeUpdate();
        }
    }

    /** Пауза в секундах после failures неудач подряд: 0 до порога, затем base, 2*base, 4*base ... не больше max. */
    static long lockSeconds(int failures, int maxFailures, Duration base, Duration max) {
        if (failures < maxFailures) return 0;
        int exponent = Math.min(failures - maxFailures, 20);
        long seconds = base.toSeconds() << exponent;
        return Math.min(seconds, max.toSeconds());
    }
}
