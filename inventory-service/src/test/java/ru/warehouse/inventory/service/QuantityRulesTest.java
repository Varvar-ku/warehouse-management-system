// src/test/java/ru/warehouse/inventory/service/QuantityRulesTest.java
package ru.warehouse.inventory.service;

import org.junit.jupiter.api.Test;
import ru.warehouse.common.exception.ApiException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityRulesTest {

    @Test
    void integerUnit_rejectsFractions() {
        assertThatThrownBy(() -> QuantityRules.require("PCS", false, new BigDecimal("1.5"), "Количество"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void integerUnit_acceptsWholeNumbers_evenWithTrailingZeros() {
        assertThatCode(() -> QuantityRules.require("PCS", false, new BigDecimal("10.0000"), "Количество"))
                .doesNotThrowAnyException();
    }

    @Test
    void fractionalUnit_acceptsFractions() {
        assertThatCode(() -> QuantityRules.require("KG", true, new BigDecimal("1.5"), "Количество"))
                .doesNotThrowAnyException();
    }
}