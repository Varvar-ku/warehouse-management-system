package ru.warehouse.report.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.warehouse.common.exception.ApiException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Границы периода: даты from/to включительно, сутки считаются в app.report.zone. */
@Component
public class ReportPeriods {

    private final ZoneId zone;

    public ReportPeriods(@Value("${app.report.zone:Europe/Moscow}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public void validate(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.validation("Дата «с» позже даты «по»");
        }
    }

    public Instant start(LocalDate from) {
        return from == null ? null : from.atStartOfDay(zone).toInstant();
    }

    /** Правая граница НЕ включается: начало суток после «по». */
    public Instant endExclusive(LocalDate to) {
        return to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
