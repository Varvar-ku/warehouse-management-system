package ru.warehouse.inventory.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.warehouse.common.numbering.DocumentNumbers;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** L6: номера документов: счёт заново каждый год, год по часовому поясу предприятия, без дублей при гонке. */
class DocumentNumbersIntegrationTest extends IntegrationTestBase {
    @Autowired JdbcClient jdbcClient;

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM document_counters WHERE prefix = 'TX'");
    }

    private DocumentNumbers at(String instant) {
        return new DocumentNumbers(jdbcClient, MOSCOW, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void numbersGrowByOne_andResetEveryYear() {
        DocumentNumbers in2026 = at("2026-06-01T10:00:00Z");
        assertThat(in2026.next("TX")).isEqualTo("TX-2026-000001");
        assertThat(in2026.next("TX")).isEqualTo("TX-2026-000002");

        DocumentNumbers in2027 = at("2027-02-01T10:00:00Z");
        assertThat(in2027.next("TX")).isEqualTo("TX-2027-000001");
        assertThat(in2026.next("TX")).isEqualTo("TX-2026-000003"); // счётчики лет независимы
    }

    @Test
    void yearIsTakenInEnterpriseTimeZone_notUtc() {
        // 31 декабря 21:30 UTC уже 1 января 00:30 в Москве
        assertThat(at("2026-12-31T21:30:00Z").next("TX")).isEqualTo("TX-2027-000001");
        assertThat(at("2026-12-31T20:30:00Z").next("TX")).isEqualTo("TX-2026-000001");
    }

    @Test
    void parallelCalls_giveUniqueContiguousNumbers() throws Exception {
        DocumentNumbers numbers = at("2026-06-01T10:00:00Z");
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<String> task = () -> {
                    gate.await();
                    return numbers.next("TX");
                };
                futures.add(pool.submit(task));
            }
            gate.countDown();
            List<String> result = new ArrayList<>();
            for (Future<String> f : futures) result.add(f.get());
            assertThat(result).doesNotHaveDuplicates().hasSize(threads);
            assertThat(result).contains("TX-2026-000001", "TX-2026-000010");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void invalidPrefix_isRejected() {
        DocumentNumbers numbers = at("2026-06-01T10:00:00Z");
        assertThatThrownBy(() -> numbers.next("x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> numbers.next("TOOLONGPREFIX")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void migrationSeededTheCurrentYearCounters() {
        // после миграции V14 счётчики текущего года продолжают прежние последовательности (строки не удалены тестом: префиксы не TX)
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM document_counters WHERE prefix IN ('RC','IS','WO','SH','AD')",
                Integer.class);
        assertThat(rows).isEqualTo(5);
    }
}
