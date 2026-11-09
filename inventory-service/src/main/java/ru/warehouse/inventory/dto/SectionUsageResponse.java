package ru.warehouse.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Чем занята секция: нужен warehouse-service, чтобы не архивировать секцию с товаром. */
public record SectionUsageResponse(UUID sectionId, boolean hasStock, BigDecimal reservedQuantity,
                                   long pendingPlacements, boolean inUse) {}
