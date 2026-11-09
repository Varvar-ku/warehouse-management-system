package ru.warehouse.catalog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.catalog.dto.CatalogDtos.*;
import ru.warehouse.catalog.entity.*;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.catalog.repository.*;

import java.util.*;

/** Справочники: типы деталей, единицы измерения, определения атрибутов. */
@Service
@RequiredArgsConstructor
public class ReferenceService {
    private final ProductTypeRepository typeRepository;
    private final UnitRepository unitRepository;
    private final AttributeDefinitionRepository definitionRepository;
    private final CatalogAudit audit;

    // ----- типы деталей -----
    @Transactional(readOnly = true)
    public List<ProductTypeResponse> listTypes(boolean includeInactive) {
        var types = includeInactive ? typeRepository.findAllByOrderByCodeAsc()
                : typeRepository.findByActiveTrueOrderByCodeAsc();
        return types.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProductTypeResponse createType(ProductTypeRequest req) {
        String code = normalizeCode(req.code());
        if (typeRepository.existsByCode(code)) {
            throw ApiException.conflict("PRODUCT_TYPE_EXISTS", "Тип с кодом уже существует: " + code);
        }
        ProductType t = new ProductType();
        t.setCode(code);
        t.setName(req.name().trim());
        t.setDescription(req.description());
        t = typeRepository.save(t);
        audit.record("product_type.created", "PRODUCT_TYPE", t.getId(), "code", t.getCode(), "name", t.getName());
        return toResponse(t);
    }

    @Transactional
    public ProductTypeResponse updateType(UUID id, ProductTypeUpdateRequest req) {
        ProductType t = typeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_TYPE_NOT_FOUND", "Тип не найден"));
        if (req.name() != null) t.setName(req.name().trim());
        if (req.description() != null) t.setDescription(req.description());
        boolean activeBefore = t.isActive();
        if (req.active() != null) t.setActive(req.active()); // деактивация вместо удаления
        audit.record("product_type.updated", "PRODUCT_TYPE", t.getId(), "code", t.getCode(), "name", t.getName(),
                "activeBefore", activeBefore, "activeAfter", t.isActive());
        return toResponse(t);
    }

    // ----- единицы измерения -----
    @Transactional(readOnly = true)
    public List<UnitResponse> listUnits() {
        return unitRepository.findAllByOrderByCodeAsc().stream()
                .map(u -> new UnitResponse(u.getId(), u.getCode(), u.getName(), u.isAllowFractions(), u.getDimension())).toList();    }

    @Transactional
    public UnitResponse createUnit(UnitRequest req) {
        String code = normalizeCode(req.code());
        if (unitRepository.existsByCode(code)) {
            throw ApiException.conflict("UNIT_EXISTS", "Единица с кодом уже существует: " + code);
        }
        Unit u = new Unit();
        u.setCode(code);
        u.setName(req.name().trim());
        u.setAllowFractions(req.allowFractions() == null || req.allowFractions());
        u.setDimension(req.dimension() == null ? UnitDimension.OTHER : req.dimension());
        u = unitRepository.save(u);
        audit.record("unit.created", "UNIT", u.getId(), "code", u.getCode(), "allowFractions", u.isAllowFractions(), "dimension", u.getDimension().name());
        return new UnitResponse(u.getId(), u.getCode(), u.getName(), u.isAllowFractions(), u.getDimension());    }

    // ----- определения атрибутов -----
    @Transactional(readOnly = true)
    public List<AttributeDefinitionResponse> listDefinitions() {
        return definitionRepository.findAllByOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AttributeDefinitionResponse createDefinition(AttributeDefinitionRequest req) {
        String code = normalizeCode(req.code());
        if (definitionRepository.existsByCode(code)) {
            throw ApiException.conflict("ATTRIBUTE_EXISTS", "Атрибут уже существует: " + code);
        }
        boolean reference = req.dataType() == AttributeDataType.REFERENCE;
        Set<String> options = req.options() == null ? Set.of() : req.options();
        if (reference && options.isEmpty()) {
            throw ApiException.validation("Для справочного атрибута нужен непустой список значений options");
        }
        if (!reference && !options.isEmpty()) {
            throw ApiException.validation("options допустим только для типа REFERENCE");
        }
        if (req.dimension() != null) {
            if (req.dataType() != AttributeDataType.NUMBER) {
                throw ApiException.validation("Тип величины (dimension) задаётся только для числового атрибута");
            }
            if (req.dimension() != UnitDimension.LENGTH && req.dimension() != UnitDimension.MASS) {
                throw ApiException.validation("Для атрибута допустим тип величины LENGTH или MASS");
            }
        }
        AttributeDefinition d = new AttributeDefinition();
        d.setCode(code);
        d.setDimension(req.dimension());
        d.setName(req.name().trim());
        d.setDataType(req.dataType());
        d.setOptions(new HashSet<>(options));
        d = definitionRepository.save(d);
        audit.record("attribute_definition.created", "ATTRIBUTE_DEFINITION", d.getId(), "code", d.getCode(),
                "dataType", d.getDataType().name());
        return toResponse(d);
    }

    // Коды храним в верхнем регистре, чтобы «pcs» и «PCS» не считались разными
    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private ProductTypeResponse toResponse(ProductType t) {
        return new ProductTypeResponse(t.getId(), t.getCode(), t.getName(), t.getDescription(), t.isActive());
    }

    private AttributeDefinitionResponse toResponse(AttributeDefinition d) {
        return new AttributeDefinitionResponse(d.getId(), d.getCode(), d.getName(), d.getDataType(),
                d.isActive(), Set.copyOf(d.getOptions()), d.getDimension());
    }
}