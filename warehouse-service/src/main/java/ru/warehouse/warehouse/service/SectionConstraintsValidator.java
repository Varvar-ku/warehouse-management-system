package ru.warehouse.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.warehouse.dto.WarehouseDtos.SectionConstraints;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * M5: ограничения секции должны ссылаться на существующие значения справочников каталога. Иначе опечатка в материале
 * или единице вместимости не даёт ошибки, а позиции молча уходят в «Нераспределено» (секция их «никогда не примет»).
 * Вызывается контроллером ДО транзакции сервиса (запросы к catalog-service не должны держать транзакцию).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SectionConstraintsValidator {
    private final CatalogReferences catalog;

    @Value("${app.placement.validate-against-catalog:true}")
    private boolean enabled;

    public void validate(SectionConstraints c) {
        if (!enabled || c == null) return;
        if (c.capacityUnitCode() != null && !c.capacityUnitCode().isBlank()) {
            require("Единица вместимости", Set.of(CatalogReferences.normalize(c.capacityUnitCode())), catalog.unitCodes());
        }
        if (c.allowedProductTypes() != null && !c.allowedProductTypes().isEmpty()) {
            require("Тип детали", normalized(c.allowedProductTypes()), catalog.productTypeCodes());
        }
        if (c.allowedMaterials() != null && !c.allowedMaterials().isEmpty()) {
            Set<String> known = catalog.materialCodes();
            if (known.isEmpty()) {
                log.warn("В каталоге нет значений атрибута MATERIAL: проверка материалов секции пропущена");
            } else {
                require("Материал", normalized(c.allowedMaterials()), known);
            }
        }
    }

    private static Set<String> normalized(Set<String> values) {
        return values.stream().map(CatalogReferences::normalize).filter(v -> !v.isEmpty()).collect(Collectors.toSet());
    }

    private static void require(String what, Set<String> given, Set<String> known) {
        Set<String> unknown = new TreeSet<>(given);
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw ApiException.validation(what + ": нет в справочнике каталога: " + unknown
                    + ". Допустимые значения: " + new TreeSet<>(known));
        }
    }
}
