// SectionService.java
package ru.warehouse.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.warehouse.dto.WarehouseDtos.*;
import ru.warehouse.warehouse.entity.Section;
import ru.warehouse.warehouse.entity.SectionStatus;
import ru.warehouse.warehouse.entity.Warehouse;
import ru.warehouse.warehouse.repository.PlacementRuleRepository;
import ru.warehouse.warehouse.repository.SectionRepository;
import ru.warehouse.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SectionService {
    // Буквы (латиница или кириллица), цифры, . _ -; пример из ТЗ: «МА-2»
    private static final Pattern CODE = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N}._-]{0,49}$");
    private static final Set<String> SORT_FIELDS = Set.of("code", "name", "priority");

    private final SectionRepository sectionRepository;
    private final WarehouseRepository warehouseRepository;
    private final PlacementRuleRepository ruleRepository;

    @Transactional
    public SectionResponse create(CreateSectionRequest req) {
        Warehouse w = warehouseRepository.findById(req.warehouseId())
                .orElseThrow(() -> ApiException.validation("Склад не найден"));
        if (!w.isActive()) {
            throw ApiException.conflict("WAREHOUSE_INACTIVE", "Склад отключён: секции в нём создавать нельзя");
        }
        String code = normalizeCode(req.code());
        if (sectionRepository.existsByWarehouseIdAndCode(w.getId(), code)) {
            throw ApiException.conflict("SECTION_CODE_EXISTS", "В складе уже есть секция с кодом " + code);
        }
        Section s = new Section();
        s.setWarehouse(w);
        s.setCode(code);
        s.setName(req.name().trim());
        s.setSectionType(blankToNull(req.sectionType()));
        s.setZone(blankToNull(req.zone()));
        s.setRack(blankToNull(req.rack()));
        s.setPriority(req.priority() == null ? 0 : req.priority());
        applyConstraints(s, req.constraints());
        s = sectionRepository.save(s);
        log.info("AUDIT: создана секция {} в складе {} ({})", s.getCode(), w.getCode(), s.getId());
        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public PageResponse<SectionResponse> search(UUID warehouseId, SectionStatus status, String text, String zone,
                                                boolean includeArchived, int page, int size,
                                                String sortBy, String direction) {
        if (!SORT_FIELDS.contains(sortBy)) {
            throw ApiException.validation("Сортировка допустима по полям: " + SORT_FIELDS);
        }
        Sort.Direction dir = Sort.Direction.fromOptionalString(direction)
                .orElseThrow(() -> ApiException.validation("direction должен быть asc или desc"));
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(dir, sortBy).and(Sort.by("id")));

        Specification<Section> spec = (root, query, cb) -> cb.conjunction();
        if (warehouseId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("warehouse").get("id"), warehouseId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        } else if (!includeArchived) {
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("status"), SectionStatus.ARCHIVED));
        }
        if (zone != null && !zone.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("zone"), zone.trim()));
        }
        if (text != null && !text.isBlank()) {
            String escaped = text.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String pattern = "%" + escaped + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), pattern, '\\'),
                    cb.like(cb.lower(root.get("name")), pattern, '\\')));
        }
        return PageResponse.from(sectionRepository.findAll(spec, pageable).map(SectionService::toResponse));
    }

    @Transactional(readOnly = true)
    public SectionResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public SectionResponse update(UUID id, UpdateSectionRequest req) {
        Section s = find(id);
        if (s.getStatus() == SectionStatus.ARCHIVED) {
            throw ApiException.conflict("SECTION_ARCHIVED", "Архивную секцию изменять нельзя");
        }
        if (req.name() != null) s.setName(req.name().trim());
        if (req.sectionType() != null) s.setSectionType(blankToNull(req.sectionType()));
        if (req.zone() != null) s.setZone(blankToNull(req.zone()));
        if (req.rack() != null) s.setRack(blankToNull(req.rack()));
        if (req.priority() != null) s.setPriority(req.priority());
        if (req.constraints() != null) {
            if (s.isUnassignedZone()) {
                throw ApiException.conflict("UNASSIGNED_ZONE_PROTECTED", "У зоны «Нераспределено» ограничений быть не может");
            }
            applyConstraints(s, req.constraints());
            // TODO этап 4: при сужении ограничений проверять остатки секции в inventory-service
        }
        if (req.status() != null) changeStatus(s, req.status());
        s.touch();
        log.info("AUDIT: изменена секция {} ({})", s.getCode(), s.getId());
        return toResponse(s);
    }

    // ================= вспомогательные методы =================

    private Section find(UUID id) {
        return sectionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("SECTION_NOT_FOUND", "Секция не найдена"));
    }

    /** Допустимые переходы: ACTIVE ↔ BLOCKED, из любого активного состояния в ARCHIVED. Из ARCHIVED выхода нет. */
    private void changeStatus(Section s, SectionStatus target) {
        SectionStatus current = s.getStatus();
        if (current == target) return;
        if (s.isUnassignedZone()) {
            throw ApiException.conflict("UNASSIGNED_ZONE_PROTECTED", "Статус зоны «Нераспределено» менять нельзя");
        }
        boolean allowed = (current == SectionStatus.ACTIVE && target != SectionStatus.ACTIVE)
                || (current == SectionStatus.BLOCKED && target != SectionStatus.BLOCKED);
        if (!allowed) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION",
                    "Переход " + current + " → " + target + " не допускается");
        }
        if (target == SectionStatus.ARCHIVED && ruleRepository.existsByTargetSectionIdAndActiveTrue(s.getId())) {
            throw ApiException.conflict("SECTION_USED_BY_RULE",
                    "Секция указана как цель активного правила размещения: сначала отключите правило");
        }
        // TODO этап 4: для BLOCKED и ARCHIVED проверить остатки и открытые задания в inventory-service
        s.setStatus(target);
    }

    private void applyConstraints(Section s, SectionConstraints c) {
        SectionConstraints x = c != null ? c
                : new SectionConstraints(null, null, null, null, null, null, null, null, null, null);
        checkRange("Длина", x.lengthMin(), x.lengthMax());
        checkRange("Ширина", x.widthMin(), x.widthMax());
        checkRange("Высота", x.heightMin(), x.heightMax());
        boolean hasUnit = x.capacityUnitCode() != null && !x.capacityUnitCode().isBlank();
        if ((x.maxCapacity() != null) != hasUnit) {
            throw ApiException.validation("Вместимость и её единица (maxCapacity, capacityUnitCode) задаются только вместе");
        }
        s.setLengthMin(x.lengthMin());
        s.setLengthMax(x.lengthMax());
        s.setWidthMin(x.widthMin());
        s.setWidthMax(x.widthMax());
        s.setHeightMin(x.heightMin());
        s.setHeightMax(x.heightMax());
        s.setMaxCapacity(x.maxCapacity());
        s.setCapacityUnitCode(hasUnit ? x.capacityUnitCode().trim().toUpperCase(Locale.ROOT) : null);
        s.setAllowedProductTypes(normalizeSet(x.allowedProductTypes()));
        s.setAllowedMaterials(normalizeSet(x.allowedMaterials()));
    }

    private static void checkRange(String name, BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw ApiException.validation(name + ": минимум больше максимума");
        }
    }

    private static Set<String> normalizeSet(Set<String> source) {
        if (source == null) return new HashSet<>();
        return source.stream().map(v -> v.trim().toUpperCase(Locale.ROOT))
                .filter(v -> !v.isEmpty()).collect(Collectors.toCollection(HashSet::new));
    }

    private static String normalizeCode(String raw) {
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw ApiException.validation("Код секции: 1–50 символов (буквы, цифры, . _ -), например MA-2");
        }
        if (Section.UNASSIGNED_CODE.equals(code)) {
            throw ApiException.validation("Код " + code + " зарезервирован для зоны «Нераспределено»");
        }
        // «МА-2» с кириллической «М» и «MA-2» с латинской визуально неотличимы: смесь алфавитов запрещаем
        boolean latin = false;
        boolean cyrillic = false;
        for (char ch : code.toCharArray()) {
            if (!Character.isLetter(ch)) continue;
            Character.UnicodeScript script = Character.UnicodeScript.of(ch);
            if (script == Character.UnicodeScript.LATIN) latin = true;
            if (script == Character.UnicodeScript.CYRILLIC) cyrillic = true;
        }
        if (latin && cyrillic) {
            throw ApiException.validation("Код секции содержит и латинские, и кириллические буквы: используйте один алфавит");
        }
        return code;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static SectionResponse toResponse(Section s) {
        SectionConstraints c = new SectionConstraints(s.getLengthMin(), s.getLengthMax(),
                s.getWidthMin(), s.getWidthMax(), s.getHeightMin(), s.getHeightMax(),
                s.getMaxCapacity(), s.getCapacityUnitCode(),
                new TreeSet<>(s.getAllowedProductTypes()), new TreeSet<>(s.getAllowedMaterials()));
        return new SectionResponse(s.getId(), s.getWarehouse().getId(), s.getCode(), s.getName(),
                s.getSectionType(), s.getZone(), s.getRack(), s.getPriority(), s.getStatus(),
                s.isUnassignedZone(), c);
    }
}