// PlacementRuleService.java
package ru.warehouse.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.warehouse.dto.WarehouseDtos.*;
import ru.warehouse.warehouse.entity.PlacementRule;
import ru.warehouse.warehouse.entity.PlacementRuleVersion;
import ru.warehouse.warehouse.entity.Section;
import ru.warehouse.warehouse.entity.SectionStatus;
import ru.warehouse.warehouse.placement.RuleConditions;
import ru.warehouse.warehouse.placement.UnitConverter;
import ru.warehouse.warehouse.repository.PlacementRuleRepository;
import ru.warehouse.warehouse.repository.PlacementRuleVersionRepository;
import ru.warehouse.warehouse.repository.SectionRepository;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementRuleService {
    private final PlacementRuleRepository ruleRepository;
    private final PlacementRuleVersionRepository versionRepository;
    private final SectionRepository sectionRepository;
    private final UnitConverter units;
    private final JsonMapper jsonMapper; // бин Spring Boot 4 (Jackson 3)
    private final WarehouseAudit audit;

    @Transactional
    public RuleResponse create(RuleRequest req, UUID actorId) {
        boolean fallback = Boolean.TRUE.equals(req.fallback());
        validateConditions(req.conditions(), fallback);
        PlacementRule r = new PlacementRule();
        r.setName(req.name().trim());
        r.setPriority(req.priority() == null ? 0 : req.priority());
        r.setFallback(fallback);
        r.setConditionsJson(write(req.conditions()));
        r.setTargetSection(requireTarget(req.targetSectionId()));
        r = ruleRepository.save(r);
        recordVersion(r, req.conditions(), actorId); // версия 1
        audit.record("placement_rule.created", "PLACEMENT_RULE", r.getId(), "name", r.getName(),
                "priority", r.getPriority(), "fallback", r.isFallback(), "targetSectionId", r.getTargetSection().getId());
        return toResponse(r);
    }

    @Transactional(readOnly = true)
    public PageResponse<RuleResponse> search(UUID warehouseId, Boolean active, Boolean fallback, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "priority").and(Sort.by("name")).and(Sort.by("id")));
        Specification<PlacementRule> spec = (root, query, cb) -> cb.conjunction();
        if (warehouseId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("targetSection").get("warehouse").get("id"), warehouseId));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        if (fallback != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("fallback"), fallback));
        }
        return PageResponse.from(ruleRepository.findAll(spec, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public RuleResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<RuleVersionResponse> versions(UUID id) {
        find(id);
        return versionRepository.findByRuleIdOrderByVersionDesc(id).stream()
                .map(v -> new RuleVersionResponse(v.getVersion(), v.getChangedAt(), v.getChangedBy(),
                        readSnapshot(v.getSnapshot())))
                .toList();
    }

    @Transactional
    public RuleResponse update(UUID id, RuleUpdateRequest req, UUID actorId) {
        PlacementRule r = find(id);
        boolean changed = false;

        if (req.name() != null && !req.name().trim().equals(r.getName())) {
            r.setName(req.name().trim());
            changed = true;
        }
        if (req.priority() != null && req.priority() != r.getPriority()) {
            r.setPriority(req.priority());
            changed = true;
        }
        if (req.active() != null && req.active() != r.isActive()) {
            r.setActive(req.active());
            changed = true;
        }
        if (req.fallback() != null && req.fallback() != r.isFallback()) {
            r.setFallback(req.fallback());
            changed = true;
        }
        if (req.conditions() != null) {
            String json = write(req.conditions());
            if (!json.equals(r.getConditionsJson())) {
                r.setConditionsJson(json);
                changed = true;
            }
        }
        if (req.targetSectionId() != null && !req.targetSectionId().equals(r.getTargetSection().getId())) {
            r.setTargetSection(requireTarget(req.targetSectionId()));
            changed = true;
        }

        // Проверяем итоговое состояние правила целиком
        RuleConditions effective = readConditions(r);
        validateConditions(effective, r.isFallback());

        if (changed) {
            r.setRuleVersion(r.getRuleVersion() + 1); // каждое изменение = новая версия
            r.touch();
            recordVersion(r, effective, actorId);
            audit.record("placement_rule.updated", "PLACEMENT_RULE", r.getId(), "name", r.getName(),
                    "version", r.getRuleVersion(), "active", r.isActive(), "priority", r.getPriority(),
                    "targetSectionId", r.getTargetSection().getId());
        }
        return toResponse(r);
    }

    // ================= вспомогательные методы =================

    private PlacementRule find(UUID id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("RULE_NOT_FOUND", "Правило размещения не найдено"));
    }

    private Section requireTarget(UUID sectionId) {
        Section s = sectionRepository.findById(sectionId)
                .orElseThrow(() -> ApiException.validation("Целевая секция не найдена"));
        if (s.isUnassignedZone()) {
            throw ApiException.validation("Зона «Нераспределено» не может быть целью правила");
        }
        if (s.getStatus() == SectionStatus.ARCHIVED) {
            throw ApiException.validation("Целевая секция в архиве");
        }
        return s;
    }

    private void validateConditions(RuleConditions c, boolean fallback) {
        if (!fallback && c.noConditions()) {
            throw ApiException.validation("Обычное правило должно содержать хотя бы одно условие "
                    + "(иначе оно подойдёт любой детали); для универсального правила используйте fallback");
        }
        c.numeric().forEach((code, cond) -> {
            if (!RuleConditions.NUMERIC_CODES.contains(code)) {
                throw ApiException.validation("Недопустимый атрибут в numeric: " + code
                        + ". Допустимы: " + RuleConditions.NUMERIC_CODES);
            }
            boolean hasRange = cond.min() != null || cond.max() != null;
            if (cond.eq() == null && !hasRange) {
                throw ApiException.validation(code + ": укажите eq либо min/max");
            }
            if (cond.eq() != null && hasRange) {
                throw ApiException.validation(code + ": eq нельзя сочетать с min/max");
            }
            if (cond.min() != null && cond.max() != null && cond.min().compareTo(cond.max()) > 0) {
                throw ApiException.validation(code + ": min больше max");
            }
            if (!units.isKnown(cond.unit())) {
                throw ApiException.validation(code + ": неизвестная единица длины " + cond.unit());
            }
        });
    }

    private void recordVersion(PlacementRule r, RuleConditions conditions, UUID actorId) {
        RuleSnapshot snapshot = new RuleSnapshot(r.getRuleVersion(), r.getName(), r.getPriority(), r.isActive(),
                r.isFallback(), conditions, r.getTargetSection().getId());
        PlacementRuleVersion v = new PlacementRuleVersion();
        v.setRuleId(r.getId());
        v.setVersion(r.getRuleVersion());
        v.setSnapshot(write(snapshot));
        v.setChangedBy(actorId);
        versionRepository.save(v);
    }

    private String write(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    private RuleConditions readConditions(PlacementRule r) {
        try {
            return jsonMapper.readValue(r.getConditionsJson(), RuleConditions.class);
        } catch (JacksonException e) {
            log.error("Повреждён conditions_json у правила {}", r.getId(), e);
            throw ApiException.conflict("RULE_CORRUPTED", "Условия правила повреждены, пересохраните правило");
        }
    }

    private RuleSnapshot readSnapshot(String json) {
        try {
            return jsonMapper.readValue(json, RuleSnapshot.class);
        } catch (JacksonException e) {
            log.error("Повреждён снимок версии правила", e);
            return null;
        }
    }

    private RuleResponse toResponse(PlacementRule r) {
        Section target = r.getTargetSection();
        return new RuleResponse(r.getId(), target.getWarehouse().getId(), r.getName(), r.getPriority(),
                r.isActive(), r.isFallback(), r.getRuleVersion(), readConditions(r),
                target.getId(), target.getCode());
    }
}