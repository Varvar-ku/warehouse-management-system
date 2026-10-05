// PlacementModels.java
package ru.warehouse.warehouse.placement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Входные и выходные модели расчёта; они же используются как DTO API. */
public final class PlacementModels {
    private PlacementModels() {}

    /** Значение числового атрибута; unitCode = null означает единицу по умолчанию (мм). */
    public record NumericValue(@NotNull BigDecimal value, @Size(max = 20) String unitCode) {}

    /** Характеристики одной позиции поступления. */
    public record PlacementItem(
            @NotBlank @Size(max = 100) String lineId,        // идентификатор строки вызывающей стороны, возвращается в ответе
            UUID productId,                                  // нужен для консолидации (остаток этой номенклатуры в секции)
            @NotBlank String productTypeCode,
            String material,
            Map<String, @Valid NumericValue> attributes,     // LENGTH, WIDTH, HEIGHT, DIAMETER
            @NotNull @Positive BigDecimal quantity,
            @NotBlank String unitCode) {

        /** Атрибут по коду без учёта регистра или null. */
        public NumericValue attribute(String code) {
            if (attributes == null) return null;
            for (var e : attributes.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(code)) return e.getValue();
            }
            return null;
        }
    }

    /**Текущая занятость секции; считает и передаёт inventory-service (остатки принадлежат ему). */
    public record SectionOccupancy(
            @NotNull UUID sectionId,
            Map<String, @PositiveOrZero BigDecimal> usedByUnit,   // код единицы → занято в этой единице
            Set<UUID> productIds) {}                              // какие номенклатуры уже лежат в секции

    public record PlacementRequest(
            @NotNull UUID warehouseId,
            @NotEmpty @Size(max = 200) List<@Valid PlacementItem> items,
            List<@Valid SectionOccupancy> occupancy) {}

    public enum PlacementStatus { ASSIGNED, UNASSIGNED }

    /** Как выбрана секция. */
    public enum PlacementStrategy {
        RULE,               // по правилу размещения
        SECTION_PRIORITY,   // правил нет, выбрана подходящая секция по приоритету
        FALLBACK_RULE,      // сработало резервное правило
        UNASSIGNED_ZONE     // подходящей секции нет, позиция в «Нераспределено»
    }

    /** Допустимая секция (для выбора кладовщиком альтернативы); freeCapacity = null, если вместимость не ограничена. */
    public record Candidate(UUID sectionId, String sectionCode, BigDecimal freeCapacity) {}

    /**
     * Результат по позиции: секция и основание выбора (правило, версия, время расчёта; п. 9 ТЗ).
     * requiresStorekeeperAction = true: нужно решение кладовщика (задачу создаст inventory-service).
     */
    public record PlacementResult(
            String lineId,
            PlacementStatus status,
            PlacementStrategy strategy,
            UUID sectionId,
            String sectionCode,
            UUID ruleId,
            Integer ruleVersion,
            String ruleName,
            OffsetDateTime calculatedAt,
            boolean requiresStorekeeperAction,
            List<String> notes,            // пояснения: почему отклонены правила и секции
            List<Candidate> candidates) {}
}