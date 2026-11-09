// PlacementService.java
package ru.warehouse.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.warehouse.entity.PlacementRule;
import ru.warehouse.warehouse.entity.Section;
import ru.warehouse.warehouse.entity.SectionStatus;
import ru.warehouse.warehouse.entity.Warehouse;
import ru.warehouse.warehouse.placement.PlacementEngine;
import ru.warehouse.warehouse.placement.PlacementEngine.RuleView;
import ru.warehouse.warehouse.placement.PlacementEngine.SectionView;
import ru.warehouse.warehouse.placement.PlacementModels.PlacementRequest;
import ru.warehouse.warehouse.placement.PlacementModels.PlacementResult;
import ru.warehouse.warehouse.placement.PlacementModels.SectionFit;
import ru.warehouse.warehouse.placement.PlacementModels.ValidateRequest;
import ru.warehouse.warehouse.placement.RuleConditions;
import ru.warehouse.warehouse.repository.PlacementRuleRepository;
import ru.warehouse.warehouse.repository.SectionRepository;
import ru.warehouse.warehouse.repository.WarehouseRepository;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Загружает данные склада и передаёт их движку. Ничего не сохраняет. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementService {
    private final WarehouseRepository warehouseRepository;
    private final SectionRepository sectionRepository;
    private final PlacementRuleRepository ruleRepository;
    private final PlacementEngine engine;
    private final JsonMapper jsonMapper;

    @Transactional(readOnly = true)
    public List<PlacementResult> recommend(PlacementRequest req) {
        Warehouse w = warehouseRepository.findById(req.warehouseId())
                .orElseThrow(() -> ApiException.notFound("WAREHOUSE_NOT_FOUND", "Склад не найден"));
        if (!w.isActive()) {
            throw ApiException.conflict("WAREHOUSE_INACTIVE", "Склад отключён");
        }
        Set<String> lineIds = new HashSet<>();
        for (var item : req.items()) {
            if (!lineIds.add(item.lineId())) {
                throw ApiException.validation("lineId повторяется: " + item.lineId());
            }
        }
        List<SectionView> sections = sectionRepository
                .findByWarehouseIdAndStatusNot(w.getId(), SectionStatus.ARCHIVED).stream()
                .map(PlacementService::toView).toList();
        List<RuleView> rules = ruleRepository.findActiveByWarehouse(w.getId()).stream()
                .map(this::toView).filter(Objects::nonNull).toList();

        return engine.calculate(sections, rules, req.items(),
                req.occupancy() == null ? List.of() : req.occupancy(), OffsetDateTime.now());
    }

    /**
     * Подходит ли конкретная секция позиции. Зону «Нераспределено» не проверяем: туда можно только
     * по решению кладовщика, это контролирует inventory-service.
     */
    @Transactional(readOnly = true)
    public SectionFit validate(ValidateRequest req) {
        Section s = sectionRepository.findById(req.sectionId())
                .orElseThrow(() -> ApiException.notFound("SECTION_NOT_FOUND", "Секция не найдена"));
        if (!s.getWarehouse().getId().equals(req.warehouseId())) {
            throw ApiException.validation("Секция принадлежит другому складу");
        }
        if (s.isUnassignedZone()) {
            return new SectionFit(true, null);
        }
        String reason = engine.violationFor(toView(s), req.item());
        return new SectionFit(reason == null, reason);
    }

    private static SectionView toView(Section s) {
        return new SectionView(s.getId(), s.getCode(), s.getPriority(), s.isUnassignedZone(), s.getStatus(),
                Set.copyOf(s.getAllowedProductTypes()), Set.copyOf(s.getAllowedMaterials()),
                s.getLengthMin(), s.getLengthMax(), s.getWidthMin(), s.getWidthMax(),
                s.getHeightMin(), s.getHeightMax(), s.getMaxCapacity(), s.getCapacityUnitCode());
    }

    /** Повреждённое правило пропускаем (и пишем в лог), чтобы одна запись не остановила весь расчёт. */
    private RuleView toView(PlacementRule r) {
        try {
            RuleConditions c = jsonMapper.readValue(r.getConditionsJson(), RuleConditions.class);
            return new RuleView(r.getId(), r.getRuleVersion(), r.getName(), r.getPriority(), r.isFallback(),
                    c, r.getTargetSection().getId());
        } catch (JacksonException e) {
            log.error("Правило {} пропущено: повреждён conditions_json", r.getId(), e);
            return null;
        }
    }
}