// PlacementTaskService.java
package ru.warehouse.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.inventory.client.WarehouseClient;
import ru.warehouse.inventory.client.WarehouseContract.PlacementItem;
import ru.warehouse.inventory.client.WarehouseContract.SectionFit;
import ru.warehouse.inventory.client.WarehouseContract.SectionInfo;
import ru.warehouse.inventory.client.WarehouseContract.ValidateRequest;
import ru.warehouse.inventory.dto.InventoryDtos.*;
import ru.warehouse.inventory.entity.PlacementTask;
import ru.warehouse.inventory.repository.PlacementTaskRepository;
import java.util.List;
import java.util.UUID;

/** Задания на размещение: просмотр и оркестрация подтверждения (проведение выполняет StockPostingService). */
@Service
@RequiredArgsConstructor
public class PlacementTaskService {
    private final PlacementTaskRepository taskRepository;
    private final WarehouseClient warehouseClient;
    private final StockPostingService postingService;

    /**
     * Кладовщик видит все задания, работник только открытые, назначенные ему или никому.
     * status по умолчанию: для работника OPEN, для кладовщика без фильтра. Не более 200 заданий (M11).
     */
    @Transactional(readOnly = true)
    public List<PlacementTaskResponse> list(PlacementTask.Status status, boolean storekeeper, UUID userId) {
        PlacementTask.Status effective = storekeeper ? status : (status == null ? PlacementTask.Status.OPEN : status);
        Specification<PlacementTask> spec = (root, query, cb) -> cb.conjunction();
        if (effective != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), effective));
        }
        if (!storekeeper) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.equal(root.get("assigneeId"), userId), cb.isNull(root.get("assigneeId"))));
        }
        // M11: не более 200 свежих заданий, ограничение выполняется в БД
        var page = PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt"));
        return taskRepository.findAll(spec, page).getContent()
                .stream().map(InventoryMapper::toTaskResponse).toList();
    }

    @Transactional(readOnly = true)
    public PlacementTaskResponse get(UUID id, boolean storekeeper, UUID userId) {
        PlacementTask task = taskRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "Задание не найдено"));
        if (!storekeeper && task.getAssigneeId() != null && !task.getAssigneeId().equals(userId)) {
            throw ApiException.notFound("TASK_NOT_FOUND", "Задание не найдено"); // чужое задание не раскрываем
        }
        return InventoryMapper.toTaskResponse(task);
    }

    /** Без @Transactional: сначала вызов warehouse-service, потом короткая транзакция проведения. */
    public PlacementTaskResponse confirmItem(UUID taskId, UUID itemId, ConfirmPlacementRequest req, UUID workerId) {
        SectionInfo section = warehouseClient.section(req.sectionId());
        requireSuitable(section, postingService.placementItemFor(taskId, itemId, req.quantity()));
        return postingService.confirmPlacement(taskId, itemId, section, req.quantity(), req.comment(), workerId);
    }

    public PlacementTaskResponse resolve(UUID taskId, UUID itemId, ResolveRequest req, UUID storekeeperId) {
        SectionInfo newTarget = req.newTargetSectionId() == null ? null : warehouseClient.section(req.newTargetSectionId());
        if (newTarget != null && req.decision() != ResolveDecision.RETURN_TO_PLACEMENT) {
            throw ApiException.validation("newTargetSectionId допустим только для RETURN_TO_PLACEMENT");
        }
        if (newTarget != null) {
            requireSuitable(newTarget, postingService.placementItemFor(taskId, itemId, null));
        }
        SectionInfo placed = req.decision() == ResolveDecision.ACCEPT_ACTUAL
                ? warehouseClient.section(postingService.placedSectionId(taskId, itemId)) : null;
        if (placed != null) {
            // та же проверка пригодности, что и при размещении работником, но на актуальных правилах секции;
            // статус и «Нераспределено» проверяет StockPostingService внутри транзакции
            requireSuitable(placed, postingService.placementItemForPlaced(taskId, itemId));
        }
        return postingService.resolve(taskId, itemId, req, newTarget, placed, storekeeperId);
    }

    /**
     * Секция должна подходить позиции по типу, материалу, размерам и предельной вместимости (решение: работник не
     * может провести размещение в неподходящую секцию). Статус и «Нераспределено» проверяет StockPostingService.
     */
    private void requireSuitable(SectionInfo section, PlacementItem item) {
        if (section.unassignedZone() || !"ACTIVE".equalsIgnoreCase(section.status())) return;
        SectionFit fit = warehouseClient.validate(new ValidateRequest(section.warehouseId(), section.id(), item));
        if (!fit.fits()) {
            throw ApiException.conflict("SECTION_NOT_SUITABLE",
                    "Секция «" + section.code() + "» не подходит: " + fit.reason());
        }
    }
}