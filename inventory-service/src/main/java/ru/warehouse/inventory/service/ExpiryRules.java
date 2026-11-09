// ExpiryRules.java
package ru.warehouse.inventory.service;

import ru.warehouse.inventory.repository.StockRepository;
import java.time.OffsetDateTime;

/**
 * Правило срока годности. "Годен до" включительно: партия годна весь последний день срока.
 * Граница общая с резервированием (StockRepository.expiryCutoff), поэтому принятое
 * поступление всегда можно резервировать, пока срок не истёк.
 */
public final class ExpiryRules {
    private ExpiryRules() {}

    /** expiryAt == null означает "срок не ограничен". */
    public static boolean isExpired(OffsetDateTime expiryAt, OffsetDateTime now) {
        return expiryAt != null && expiryAt.isBefore(StockRepository.expiryCutoff(now));
    }

    public static String message(OffsetDateTime expiryAt) {
        return "срок годности истёк (" + expiryAt.toLocalDate() + "), просроченный товар не принимается";
    }
}
