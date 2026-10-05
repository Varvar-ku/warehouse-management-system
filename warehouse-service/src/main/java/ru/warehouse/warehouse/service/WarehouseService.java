// WarehouseService.java
package ru.warehouse.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.warehouse.dto.WarehouseDtos.*;
import ru.warehouse.warehouse.entity.Section;
import ru.warehouse.warehouse.entity.Warehouse;
import ru.warehouse.warehouse.repository.SectionRepository;
import ru.warehouse.warehouse.repository.WarehouseRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {
    private final WarehouseRepository warehouseRepository;
    private final SectionRepository sectionRepository;

    @Transactional
    public WarehouseResponse create(WarehouseRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (warehouseRepository.existsByCode(code)) {
            throw ApiException.conflict("WAREHOUSE_EXISTS", "Склад с кодом уже существует: " + code);
        }
        Warehouse w = new Warehouse();
        w.setCode(code);
        w.setName(req.name().trim());
        w.setAddress(blankToNull(req.address()));
        w = warehouseRepository.save(w);

        // Зона «Нераспределено» создаётся вместе со складом: на неё опирается fallback алгоритма подбора
        Section zone = new Section();
        zone.setWarehouse(w);
        zone.setCode(Section.UNASSIGNED_CODE);
        zone.setName("Нераспределено");
        zone.setSectionType(Section.UNASSIGNED_CODE);
        zone.setUnassignedZone(true);
        sectionRepository.save(zone);

        log.info("AUDIT: создан склад {} ({})", w.getCode(), w.getId());
        return toResponse(w);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> list() {
        return warehouseRepository.findAllByOrderByCodeAsc().stream().map(WarehouseService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public WarehouseResponse update(UUID id, WarehouseUpdateRequest req) {
        Warehouse w = find(id);
        if (req.name() != null) w.setName(req.name().trim());
        if (req.address() != null) w.setAddress(blankToNull(req.address()));
        if (req.active() != null) w.setActive(req.active());
        w.touch();
        log.info("AUDIT: изменён склад {} ({})", w.getCode(), w.getId());
        return toResponse(w);
    }

    private Warehouse find(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("WAREHOUSE_NOT_FOUND", "Склад не найден"));
    }

    private static WarehouseResponse toResponse(Warehouse w) {
        return new WarehouseResponse(w.getId(), w.getCode(), w.getName(), w.getAddress(), w.isActive());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}