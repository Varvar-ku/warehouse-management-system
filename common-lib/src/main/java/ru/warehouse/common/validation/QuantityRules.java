package ru.warehouse.common.validation;

import ru.warehouse.common.exception.ApiException;
import java.math.BigDecimal;

/** Правила количеств: у единиц без дробных значений (штуки) количество должно быть целым. */
public final class QuantityRules {
    private QuantityRules() {}

    public static void require(String unitCode, boolean allowFractions, BigDecimal quantity, String what) {
        // stripTrailingZeros: 10.0000 допустимо, 10.5 нет
        if (!allowFractions && quantity.stripTrailingZeros().scale() > 0) {
            throw ApiException.validation(what + ": единица «" + unitCode
                    + "» не допускает дробных количеств (указано " + quantity.toPlainString() + ")");
        }
    }
}
