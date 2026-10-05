// RuleConditions.java
package ru.warehouse.warehouse.placement;

import ru.warehouse.warehouse.placement.PlacementModels.NumericValue;
import ru.warehouse.warehouse.placement.PlacementModels.PlacementItem;
import java.math.BigDecimal;
import java.util.*;

/**
 * Условия правила (хранятся в conditions_json). Все заданные условия объединяются по «И».
 * Пример: {"productTypeCodes":["ARMATURE"],"materials":["METAL"],"numeric":{"LENGTH":{"eq":2000,"unit":"MM"}}}
 */
public record RuleConditions(
        List<String> productTypeCodes,
        List<String> materials,
        Map<String, NumericCondition> numeric) {

    /** Допустимые числовые атрибуты в условиях. Масса пока не поддерживается. */
    public static final Set<String> NUMERIC_CODES = Set.of("LENGTH", "WIDTH", "HEIGHT", "DIAMETER");

    /** Условие по числу: либо eq (с погрешностью), либо границы min/max. unit = null означает единицу по умолчанию. */
    public record NumericCondition(BigDecimal eq, BigDecimal min, BigDecimal max, String unit) {}

    public RuleConditions {
        productTypeCodes = normalize(productTypeCodes);
        materials = normalize(materials);
        // TreeMap: порядок ключей стабилен, одинаковые условия дают одинаковый JSON (нужно для учёта версий)
        Map<String, NumericCondition> normalized = new TreeMap<>();
        if (numeric != null) {
            numeric.forEach((key, value) -> {
                if (normalized.put(key.trim().toUpperCase(Locale.ROOT), value) != null) {
                    throw new IllegalArgumentException("Атрибут указан дважды: " + key);
                }
            });
        }
        numeric = Collections.unmodifiableMap(normalized);
    }

    private static List<String> normalize(List<String> source) {
        if (source == null) return List.of();
        return source.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    /** Условий нет вообще (подошло бы любой детали). Имя без префикса get/is: не попадёт в JSON. */
    public boolean noConditions() {
        return productTypeCodes.isEmpty() && materials.isEmpty() && numeric.isEmpty();
    }

    /** Подходит ли позиция под условия. Бросает UnknownUnitException при неизвестной единице длины. */
    public boolean matches(PlacementItem item, UnitConverter units, BigDecimal tolerance) {
        if (!productTypeCodes.isEmpty()
                && !productTypeCodes.contains(item.productTypeCode().trim().toUpperCase(Locale.ROOT))) {
            return false;
        }
        if (!materials.isEmpty()
                && (item.material() == null || !materials.contains(item.material().trim().toUpperCase(Locale.ROOT)))) {
            return false;
        }
        for (Map.Entry<String, NumericCondition> e : numeric.entrySet()) {
            NumericValue actual = item.attribute(e.getKey());
            if (actual == null) return false; // условие есть, а значения у детали нет
            BigDecimal value = units.toBase(actual.value(), actual.unitCode());
            NumericCondition c = e.getValue();
            if (c.eq() != null
                    && value.subtract(units.toBase(c.eq(), c.unit())).abs().compareTo(tolerance) > 0) {
                return false;
            }
            if (c.min() != null && value.add(tolerance).compareTo(units.toBase(c.min(), c.unit())) < 0) {
                return false;
            }
            if (c.max() != null && value.subtract(tolerance).compareTo(units.toBase(c.max(), c.unit())) > 0) {
                return false;
            }
        }
        return true;
    }
}