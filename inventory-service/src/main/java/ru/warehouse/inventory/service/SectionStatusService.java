// SectionStatusService.java
package ru.warehouse.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import ru.warehouse.common.event.SectionStatusEvent;
import ru.warehouse.inventory.repository.StockRepository;

/** Переносит статус секции в строки остатка: BLOCKED и ARCHIVED закрывают резервирование. Операция идемпотентна. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SectionStatusService {
    private final StockRepository stockRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public void handle(String json) {
        SectionStatusEvent event = jsonMapper.readValue(json, SectionStatusEvent.class);
        boolean blocked = !"ACTIVE".equalsIgnoreCase(event.status());
        int rows = stockRepository.updateSectionBlocked(event.sectionId(), blocked);
        log.info("Секция {} ({}): статус {}, строк остатка обновлено: {}",
                event.sectionCode(), event.sectionId(), event.status(), rows);
    }
}
