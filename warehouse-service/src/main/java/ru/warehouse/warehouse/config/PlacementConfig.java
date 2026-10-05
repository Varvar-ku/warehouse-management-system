// config/PlacementConfig.java
package ru.warehouse.warehouse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.warehouse.warehouse.placement.PlacementEngine;
import ru.warehouse.warehouse.placement.UnitConverter;

@Configuration
class PlacementConfig {

    @Bean
    UnitConverter unitConverter(PlacementProperties props) {
        return new UnitConverter(props);
    }

    /** Движок не зависит от БД и Spring: его можно проверять обычными unit-тестами. */
    @Bean
    PlacementEngine placementEngine(UnitConverter units, PlacementProperties props) {
        return new PlacementEngine(units, props.lengthTolerance());
    }
}