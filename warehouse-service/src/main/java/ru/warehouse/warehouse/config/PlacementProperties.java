// config/PlacementProperties.java
package ru.warehouse.warehouse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;
import java.util.Map;

/** Настройки подбора секции: warehouse.placement.* (значения по умолчанию заданы и здесь, для тестов). */
@ConfigurationProperties(prefix = "warehouse.placement")
public record PlacementProperties(
        BigDecimal lengthTolerance,        // погрешность сравнения размеров, мм
        String defaultLengthUnit,          // единица, если у значения она не указана
        Map<String, BigDecimal> lengthUnits) { // код единицы → коэффициент перевода в мм

    public PlacementProperties {
        if (lengthTolerance == null) lengthTolerance = new BigDecimal("0.5");
        if (defaultLengthUnit == null || defaultLengthUnit.isBlank()) defaultLengthUnit = "MM";
        if (lengthUnits == null || lengthUnits.isEmpty()) {
            lengthUnits = Map.of("MM", BigDecimal.ONE, "CM", BigDecimal.TEN, "M", new BigDecimal("1000"));
        }
    }
}