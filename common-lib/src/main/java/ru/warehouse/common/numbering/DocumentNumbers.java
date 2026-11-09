package ru.warehouse.common.numbering;

import org.springframework.jdbc.core.simple.JdbcClient;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Номера документов вида {@code ПРЕФИКС-ГОД-000123} (L6). Нумерация идёт заново каждый календарный год, год считается
 * по часовому поясу предприятия (а не сервера). Счётчик хранится в таблице {@code document_counters} базы сервиса
 * и увеличивается атомарно (INSERT ... ON CONFLICT DO UPDATE).
 * <p>
 * Вызывать внутри бизнес-транзакции: счётчик откатится вместе с документом (номера без пропусков), а строка
 * счётчика блокируется до завершения транзакции, то есть создание документов одного типа идёт по очереди.
 */
public class DocumentNumbers {
    private final JdbcClient jdbc;
    private final ZoneId zone;
    private final Clock clock;

    public DocumentNumbers(JdbcClient jdbc, ZoneId zone) {
        this(jdbc, zone, Clock.systemUTC());
    }

    public DocumentNumbers(JdbcClient jdbc, ZoneId zone, Clock clock) {
        this.jdbc = jdbc;
        this.zone = zone;
        this.clock = clock;
    }

    public String next(String prefix) {
        if (prefix == null || !prefix.matches("[A-Z]{2,5}")) {
            throw new IllegalArgumentException("Префикс номера: 2-5 заглавных латинских букв, получено: " + prefix);
        }
        int year = LocalDate.now(clock.withZone(zone)).getYear();
        Long value = jdbc.sql("""
                        INSERT INTO document_counters (prefix, year, last_value)
                        VALUES (:prefix, :year, 1)
                        ON CONFLICT (prefix, year) DO UPDATE SET last_value = document_counters.last_value + 1
                        RETURNING last_value
                        """)
                .param("prefix", prefix)
                .param("year", year)
                .query(Long.class)
                .single();
        return String.format("%s-%d-%06d", prefix, year, value);
    }
}
