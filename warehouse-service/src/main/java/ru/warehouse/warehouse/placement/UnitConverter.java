// UnitConverter.java
package ru.warehouse.warehouse.placement;

import ru.warehouse.warehouse.config.PlacementProperties;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Перевод длин в миллиметры по таблице коэффициентов из настроек. */
public class UnitConverter {
    private final Map<String, BigDecimal> factors = new HashMap<>();
    private final String defaultUnit;

    public UnitConverter(PlacementProperties props) {
        props.lengthUnits().forEach((code, factor) -> factors.put(code.trim().toUpperCase(Locale.ROOT), factor));
        this.defaultUnit = props.defaultLengthUnit().trim().toUpperCase(Locale.ROOT);
    }

    /** null или пустая единица означает единицу по умолчанию. */
    public boolean isKnown(String unitCode) {
        return factors.containsKey(normalize(unitCode));
    }

    public BigDecimal toBase(BigDecimal value, String unitCode) {
        BigDecimal factor = factors.get(normalize(unitCode));
        if (factor == null) {
            // Не гадаем: позиция уйдёт в «Нераспределено», кладовщик решит вручную
            throw new UnknownUnitException("Неизвестная единица длины: " + normalize(unitCode));
        }
        return value.multiply(factor);
    }

    private String normalize(String unitCode) {
        return unitCode == null || unitCode.isBlank() ? defaultUnit : unitCode.trim().toUpperCase(Locale.ROOT);
    }
}