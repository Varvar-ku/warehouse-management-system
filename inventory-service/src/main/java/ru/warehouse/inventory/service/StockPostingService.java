// StockPostingService.java
package ru.warehouse.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.inventory.client.WarehouseContract.SectionInfo;
import ru.warehouse.inventory.dto.InventoryDtos.PlacementTaskResponse;
import ru.warehouse.inventory.dto.InventoryDtos.ResolveRequest;
import ru.warehouse.inventory.entity.*;
import ru.warehouse.inventory.repository.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Проведение размещения по остатку. Каждая публичная операция выполняется в ОДНОЙ транзакции:
 * блокировка документа → проверки → партия, остаток и движение RECEIPT → завершение документа.
 * Вызовы соседних сервисов сюда не входят (делаются заранее), чтобы не держать блокировку на время HTTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPostingService {
    private final ReceiptRepository receiptRepository;
    private final PlacementTaskRepository taskRepository;
    private final BatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;

    /** Работник подтверждает фактическую секцию и количество (разд. 10 ТЗ). */
    @Transactional
    public PlacementTaskResponse confirmPlacement(UUID taskId, UUID itemId, SectionInfo section,
                                                  BigDecimal quantity, String comment, UUID workerId) {
        PlacementTask task = findTask(taskId);
        Receipt r = lockAndCheck(task);
        if (task.getAssigneeId() != null && !task.getAssigneeId().equals(workerId)) {
            throw ApiException.forbidden("TASK_NOT_ASSIGNED", "Задание назначено другому работнику");
        }
        if (!section.warehouseId().equals(r.getWarehouseId())) {
            throw ApiException.validation("Секция принадлежит другому складу");
        }
        ReceiptItem item = findItem(r, itemId);
        QuantityRules.require(item.getUnitCode(), item.isUnitAllowFractions(), quantity, "Фактическое количество");
        if (item.getPlacementStatus() != ReceiptItem.PlacementStatus.PENDING) {
            throw ApiException.conflict("ITEM_NOT_PENDING", "Позиция уже обработана: " + item.getPlacementStatus());
        }

        item.setPlacedSectionId(section.id());
        item.setPlacedSectionCode(section.code());
        item.setPlacedQuantity(quantity);
        item.setPlacedBy(workerId);
        item.setPlacedAt(OffsetDateTime.now());

        boolean exact = section.id().equals(item.getTargetSectionId())
                && quantity.compareTo(item.getQuantity()) == 0;
        if (exact) {
            post(r, item, workerId, comment);
        } else {
            // Расхождение: остаток НЕ меняется, решение за кладовщиком (разд. 10 ТЗ)
            if (comment == null || comment.isBlank()) {
                throw ApiException.validation("При расхождении с планом укажите комментарий");
            }
            item.setDiscrepancyComment(comment.trim());
            item.setPlacementStatus(ReceiptItem.PlacementStatus.DISCREPANCY);
            log.warn("AUDIT: расхождение размещения, поступление {}, позиция {}: план {} × {}, факт {} × {}",
                    r.getNumber(), item.getLineNo(), item.getTargetSectionCode(), item.getQuantity(),
                    section.code(), quantity);
        }
        finishIfDone(r, task);
        return InventoryMapper.toTaskResponse(task);
    }

    /** Решение кладовщика по расхождению. newTarget нужен только для RETURN_TO_PLACEMENT с новой секцией. */
    @Transactional
    public PlacementTaskResponse resolve(UUID taskId, UUID itemId, ResolveRequest req,
                                         SectionInfo newTarget, UUID storekeeperId) {
        PlacementTask task = findTask(taskId);
        Receipt r = lockAndCheck(task);
        ReceiptItem item = findItem(r, itemId);
        if (item.getPlacementStatus() != ReceiptItem.PlacementStatus.DISCREPANCY) {
            throw ApiException.conflict("ITEM_NOT_IN_DISCREPANCY", "Позиция не в статусе расхождения");
        }
        String note = req.comment().trim();
        switch (req.decision()) {
            case ACCEPT_ACTUAL -> post(r, item, storekeeperId, "Решение кладовщика: " + note);
            case RETURN_TO_PLACEMENT -> {
                if (newTarget != null) {
                    if (!newTarget.warehouseId().equals(r.getWarehouseId())) {
                        throw ApiException.validation("Секция принадлежит другому складу");
                    }
                    item.setTargetSectionId(newTarget.id());
                    item.setTargetSectionCode(newTarget.code());
                    item.setTargetDecision(ReceiptItem.TargetDecision.ALTERNATIVE);
                }
                item.setPlacedSectionId(null);
                item.setPlacedSectionCode(null);
                item.setPlacedQuantity(null);
                item.setPlacedBy(null);
                item.setPlacedAt(null);
                item.setDiscrepancyComment("Возврат в размещение: " + note);
                item.setPlacementStatus(ReceiptItem.PlacementStatus.PENDING);
            }
        }
        log.info("AUDIT: решение по расхождению {} (поступление {}, позиция {}), инициатор {}",
                req.decision(), r.getNumber(), item.getLineNo(), storekeeperId);
        finishIfDone(r, task);
        return InventoryMapper.toTaskResponse(task);
    }

    // ================= внутреннее =================

    /** Партия + остаток + движение RECEIPT: всё в текущей транзакции. */
    private void post(Receipt r, ReceiptItem item, UUID performedBy, String comment) {
        BigDecimal quantity = item.getPlacedQuantity();
        OffsetDateTime now = OffsetDateTime.now();

        Batch batch = new Batch();
        batch.setProductId(item.getProductId());
        batch.setProductSku(item.getProductSku());
        batch.setProductName(item.getProductName());
        batch.setUnitCode(item.getUnitCode());
        batch.setUnitAllowFractions(item.isUnitAllowFractions());
        batch.setReceiptId(r.getId());
        batch.setBatchNumber(item.effectiveBatchNumber());
        batch.setReceivedAt(r.getReceivedAt());
        batch.setExpiryAt(item.getExpiryAt());
        batch.setAttributesSnapshotJson(item.getSnapshotJson() == null ? "{}" : item.getSnapshotJson());
        batch = batchRepository.save(batch);

        Stock stock = new Stock();
        stock.setWarehouseId(r.getWarehouseId());
        stock.setProductId(item.getProductId());
        stock.setSectionId(item.getPlacedSectionId());
        stock.setSectionCode(item.getPlacedSectionCode());
        stock.setBatch(batch);
        stock.setUnitCode(item.getUnitCode());
        stock.setQuantity(quantity);
        stockRepository.save(stock);

        StockMovement movement = new StockMovement();
        movement.setMovementType(StockMovement.Type.RECEIPT);
        movement.setWarehouseId(r.getWarehouseId());
        movement.setProductId(item.getProductId());
        movement.setBatchId(batch.getId());
        movement.setToSectionId(item.getPlacedSectionId());
        movement.setToSectionCode(item.getPlacedSectionCode());
        movement.setQuantity(quantity);
        movement.setUnitCode(item.getUnitCode());
        movement.setDocumentType("RECEIPT");
        movement.setDocumentId(r.getId());
        movement.setPerformedBy(performedBy);
        movement.setPerformedAt(now);
        movement.setComment("Поступление " + r.getNumber() + ", позиция " + item.getLineNo()
                + (comment == null || comment.isBlank() ? "" : ". " + comment.trim()));
        movementRepository.save(movement);

        item.setBatchId(batch.getId());
        item.setPlacementStatus(ReceiptItem.PlacementStatus.PLACED);
        log.info("AUDIT: проведено RECEIPT {} × {} в секцию {} (поступление {}, позиция {}), исполнитель {}",
                item.getProductSku(), quantity, item.getPlacedSectionCode(), r.getNumber(), item.getLineNo(), performedBy);
    }

    /** Все позиции размещены: задание завершено, поступление в DONE. */
    private void finishIfDone(Receipt r, PlacementTask task) {
        if (r.getItems().stream().allMatch(i -> i.getPlacementStatus() == ReceiptItem.PlacementStatus.PLACED)) {
            OffsetDateTime now = OffsetDateTime.now();
            task.setStatus(PlacementTask.Status.COMPLETED);
            task.setCompletedAt(now);
            r.setStatus(Receipt.Status.DONE);
            r.setCompletedAt(now);
            r.touch();
            log.info("AUDIT: поступление {} полностью размещено", r.getNumber());
        }
    }

    private PlacementTask findTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "Задание не найдено"));
    }

    /** Блокирует документ (сериализует работников) и проверяет, что задание и документ ещё в работе. */
    private Receipt lockAndCheck(PlacementTask task) {
        Receipt r = receiptRepository.findByIdForUpdate(task.getReceipt().getId())
                .orElseThrow(() -> ApiException.notFound("RECEIPT_NOT_FOUND", "Поступление не найдено"));
        if (task.getStatus() != PlacementTask.Status.OPEN || r.getStatus() != Receipt.Status.PLACEMENT) {
            throw ApiException.conflict("TASK_NOT_OPEN", "Задание закрыто или отменено");
        }
        return r;
    }

    private static ReceiptItem findItem(Receipt r, UUID itemId) {
        return r.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "Позиция не найдена в задании"));
    }
}