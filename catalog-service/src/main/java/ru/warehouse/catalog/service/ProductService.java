package ru.warehouse.catalog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.catalog.dto.CatalogDtos.*;
import ru.warehouse.catalog.entity.*;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.catalog.repository.*;
import java.util.*;
import java.util.stream.Collectors;
import ru.warehouse.common.dto.PageResponse;

@Service
@RequiredArgsConstructor
public class ProductService {
    // Белый список полей сортировки: не даём клиенту сортировать по произвольным колонкам
    private static final Set<String> SORT_FIELDS = Set.of("sku", "name", "createdAt");

    private final ProductRepository productRepository;
    private final ProductTypeRepository typeRepository;
    private final UnitRepository unitRepository;
    private final AttributeDefinitionRepository definitionRepository;
    private final CatalogAudit audit;

    /** Для атрибутов с типом величины (длина, масса) единица значения обязательна: «1500» без единицы неоднозначно. */
    @org.springframework.beans.factory.annotation.Value("${app.catalog.require-attribute-unit:true}")
    private boolean requireAttributeUnit;

    @Transactional
    public ProductResponse create(CreateProductRequest req) {
        String sku = req.sku().trim().toUpperCase(Locale.ROOT);
        if (productRepository.existsBySku(sku)) {
            throw ApiException.conflict("SKU_ALREADY_EXISTS", "Номенклатура с артикулом уже существует: " + sku);
        }
        Product p = new Product();
        p.setSku(sku);
        p.setName(req.name().trim());
        p.setProductType(requireActiveType(req.productTypeId()));
        p.setUnit(unitRepository.findById(req.unitId()).orElseThrow(() -> ApiException.validation("Единица измерения не найдена")));
        p.setDescription(req.description());
        if (req.attributes() != null) applyAttributes(p, req.attributes());
        p = productRepository.save(p);
        audit.record("product.created", "PRODUCT", p.getId(), "sku", p.getSku(), "name", p.getName(),
                "productType", p.getProductType().getCode(), "unit", p.getUnit().getCode());
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return toResponse(find(id));
    }

    /**
     * Пакетное чтение для других сервисов. Архивные товары тоже возвращаем: на них могут
     * ссылаться исторические документы. Несуществующие id молча пропускаются,
     * вызывающая сторона сама сверяет результат со своим списком.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> findByIds(Collection<UUID> ids) {
        if (ids.size() > 200) {
            throw ApiException.validation("За один запрос можно получить не более 200 позиций");
        }
        var codes = unitCodes(); // справочник единиц читаем один раз на весь запрос
        return productRepository.findAllById(ids).stream().map(p -> toResponse(p, codes)).toList();
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest req) {
        Product p = find(id);
        if (!p.isActive()) {
            throw ApiException.conflict("PRODUCT_ARCHIVED", "Архивную номенклатуру изменять нельзя");
        }
        if (req.name() != null) p.setName(req.name().trim());
        if (req.productTypeId() != null) p.setProductType(requireActiveType(req.productTypeId()));
        if (req.description() != null) p.setDescription(req.description());
        if (req.attributes() != null) applyAttributes(p, req.attributes());
        p.touch(); // обновляем updated_at, даже если менялись только атрибуты
        List<String> changed = new ArrayList<>();
        if (req.name() != null) changed.add("name");
        if (req.productTypeId() != null) changed.add("productType");
        if (req.description() != null) changed.add("description");
        if (req.attributes() != null) changed.add("attributes");
        audit.record("product.updated", "PRODUCT", p.getId(), "sku", p.getSku(), "changedFields", changed);
        return toResponse(p); // изменения сохранятся при коммите транзакции
    }

    /** Архивирование вместо удаления. Операция идемпотентна. */
    @Transactional
    public ProductResponse archive(UUID id) {
        Product p = find(id);
        if (p.isActive()) {
            p.setActive(false);
            p.touch();
            audit.record("product.archived", "PRODUCT", p.getId(), "sku", p.getSku());
        }
        return toResponse(p);
    }

    @Transactional
    public ProductResponse restore(UUID id) {
        Product p = find(id);
        if (!p.isActive()) {
            p.setActive(true);
            p.touch();
            audit.record("product.restored", "PRODUCT", p.getId(), "sku", p.getSku());
        }
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String text, UUID typeId, boolean includeArchived, int page, int size, String sortBy, String direction) {
        if (!SORT_FIELDS.contains(sortBy)) {
            throw ApiException.validation("Сортировка допустима по полям: " + SORT_FIELDS);
        }
        Sort.Direction dir = Sort.Direction.fromOptionalString(direction).orElseThrow(() -> ApiException.validation("direction должен быть asc или desc"));
        Sort sort = Sort.by(dir, sortBy).and(Sort.by("id"));
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);

        // Фильтры добавляются только если заданы
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();
        if (!includeArchived) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("active")));
        }
        if (typeId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("productType").get("id"), typeId));
        }
        if (text != null && !text.isBlank()) {
            // экранируем служебные символы LIKE, чтобы «%» и «_» искались как обычные
            String escaped = text.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String pattern = "%" + escaped + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.get("sku")), pattern, '\\')));
        }
        // Карту id → код единицы строим один раз на всю страницу, а не на каждый товар
        var codes = unitCodes();
        return PageResponse.from(productRepository.findAll(spec, pageable).map(p -> toResponse(p, codes)));
    }

    // ================= вспомогательные методы =================

    private Product find(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Номенклатура не найдена"));
    }

    private ProductType requireActiveType(UUID id) {
        ProductType t = typeRepository.findById(id)
                .orElseThrow(() -> ApiException.validation("Тип детали не найден"));
        if (!t.isActive()) throw ApiException.validation("Тип детали деактивирован: " + t.getCode());
        return t;
    }

    /**
     * Приводит набор атрибутов товара к переданному: обновляет существующие, добавляет новые,
     * удаляет отсутствующие. Обновление «на месте» важно: Hibernate выполняет INSERT раньше DELETE,
     * и полная замена с теми же кодами нарушила бы UNIQUE(product_id, attribute_code).
     */
    private void applyAttributes(Product product, List<AttributeValueRequest> requests) {
        Map<String, AttributeValueRequest> incoming = new LinkedHashMap<>();
        for (AttributeValueRequest r : requests) {
            String code = r.attributeCode().trim().toUpperCase(Locale.ROOT);
            if (incoming.put(code, r) != null) {
                throw ApiException.validation("Атрибут указан дважды: " + code);
            }
        }
        Map<String, ProductAttribute> existing = new HashMap<>();
        product.getAttributes().forEach(a -> existing.put(a.getAttributeCode(), a));

        product.getAttributes().removeIf(a -> !incoming.containsKey(a.getAttributeCode())); // orphanRemoval удалит из БД

        incoming.forEach((code, req) -> {
            AttributeDefinition def = definitionRepository.findByCode(code).filter(AttributeDefinition::isActive).orElseThrow(() -> ApiException.validation("Неизвестный или отключённый атрибут: " + code));
            ProductAttribute attr = existing.get(code);
            if (attr == null) {
                attr = new ProductAttribute();
                attr.setProduct(product);
                attr.setAttributeCode(code);
                product.getAttributes().add(attr);
            }
            fillValue(attr, def, req);
        });
    }

    /** Проверяет значение по типу определения и записывает ровно одно поле значения. */
    private void fillValue(ProductAttribute attr, AttributeDefinition def, AttributeValueRequest req) {
        attr.setValueString(null);
        attr.setValueNumber(null);
        attr.setValueBoolean(null);
        attr.setValueUnitId(null);
        String code = def.getCode();
        switch (def.getDataType()) {
            case STRING -> {
                if (req.valueString() == null || req.valueString().isBlank()) {
                    throw ApiException.validation("Для атрибута " + code + " нужно valueString");
                }
                attr.setValueString(req.valueString().trim());
            }
            case NUMBER -> {
                if (req.valueNumber() == null) {
                    throw ApiException.validation("Для атрибута " + code + " нужно valueNumber");
                }
                Unit valueUnit = req.valueUnitId() == null ? null : unitRepository.findById(req.valueUnitId())
                        .orElseThrow(() -> ApiException.validation("Единица значения атрибута " + code + " не найдена"));
                checkDimension(def, valueUnit);
                attr.setValueNumber(req.valueNumber());
                attr.setValueUnitId(req.valueUnitId());
            }
            case BOOLEAN -> {
                if (req.valueBoolean() == null) {
                    throw ApiException.validation("Для атрибута " + code + " нужно valueBoolean");
                }
                attr.setValueBoolean(req.valueBoolean());
            }
            case REFERENCE -> {
                if (req.valueString() == null || !def.getOptions().contains(req.valueString())) {
                    throw ApiException.validation("Значение атрибута " + code + " должно быть одним из " + def.getOptions());
                }
                attr.setValueString(req.valueString());
            }
        }
    }

    /**
     * M5: единица значения должна подходить по типу величины. Иначе движок размещения не сможет перевести длину
     * (например, «1500 KG») и молча отправит позицию в «Нераспределено».
     */
    private void checkDimension(AttributeDefinition def, Unit unit) {
        UnitDimension expected = def.getDimension();
        if (expected == null) return;
        if (unit == null) {
            if (requireAttributeUnit) {
                throw ApiException.validation("Для атрибута " + def.getCode() + " укажите единицу измерения (тип: " + expected + ")");
            }
            return;
        }
        if (unit.getDimension() != expected) {
            throw ApiException.validation("Единица " + unit.getCode() + " не подходит для атрибута " + def.getCode()
                    + ": нужна единица типа " + expected + ", а у неё " + unit.getDimension());
        }
    }

    /** Одиночный ответ: загружаем справочник единиц (таблица крошечная). */
    private ProductResponse toResponse(Product p) {
        return toResponse(p, unitCodes());
    }

    /** Версия для списков: карту id → код строим один раз на весь запрос, а не на каждый товар. */
    private ProductResponse toResponse(Product p, Map<UUID, String> unitCodes) {
        List<AttributeValueResponse> attrs = p.getAttributes().stream().sorted(Comparator.comparing(ProductAttribute::getAttributeCode)).map(a -> new AttributeValueResponse(a.getAttributeCode(), a.getValueString(), a.getValueNumber(), a.getValueBoolean(), a.getValueUnitId(), a.getValueUnitId() == null ? null : unitCodes.get(a.getValueUnitId()))).toList();
        return new ProductResponse(p.getId(), p.getSku(), p.getName(),
                p.getProductType().getId(), p.getProductType().getCode(),
                p.getUnit().getId(), p.getUnit().getCode(), p.getUnit().isAllowFractions(),
                p.getDescription(), p.isActive(), attrs, p.getCreatedAt(), p.getUpdatedAt());    }

    private Map<UUID, String> unitCodes() {
        return unitRepository.findAll().stream().collect(Collectors.toMap(Unit::getId, Unit::getCode));
    }


}