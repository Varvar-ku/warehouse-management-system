package ru.warehouse.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/** Безопасное чтение значений из details аудит-события: при неверном формате возвращается null. */
final class AuditValues {

    private AuditValues() {
    }

    static String str(Object o) {
        return o == null ? null : o.toString();
    }

    static UUID uuid(Object o) {
        try {
            return o == null ? null : UUID.fromString(o.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static BigDecimal dec(Object o) {
        try {
            return o == null ? null : new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer integer(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return o == null ? null : Integer.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Instant instant(Object o) {
        try {
            return o == null ? null : OffsetDateTime.parse(o.toString()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
