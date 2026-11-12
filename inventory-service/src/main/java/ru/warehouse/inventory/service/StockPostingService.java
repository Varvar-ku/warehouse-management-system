// StockPostingService.java
package ru.warehouse.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.common.validation.QuantityRules;
import ru.warehouse.inventory.client.WarehouseContract.PlacementItem;
import ru.warehouse.inventory.client.WarehouseContract.SectionInfo;
import ru.warehouse.inventory.dto.InventoryDtos;
import ru.warehouse.inventory.dto.InventoryDtos.BatchSnapshot;
import ru.warehouse.inventory.dto.InventoryDtos.PlacementTaskResponse;
import ru.warehouse.inventory.dto.InventoryDtos.ResolveRequest;
import ru.warehouse.inventory.entity.*;
import ru.warehouse.inventory.repository.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import ru.warehouse.common.event.OutboxPublisher;
import static ru.warehouse.common.event.OutboxPublisher.details;

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
    private final ReceiptItemRepository itemRepository;
    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final OutboxPublisher outbox;
    private final BatchLookup batchLookup;

    private static final String SERVICE = "inventory-service";
    private final MovementEvents movementEvents;

    private static final List<Receipt.Status> IN_PROGRESS = List.of(Receipt.Status.RECEIVING, Receipt.Status.PLACEMENT);

    /** Характеристики позиции для проверки секции в warehouse-service (вызывается до транзакции проведения). */
    @Transactional(readOnly = true)
    public PlacementItem placementItemFor(UUID taskId, UUID itemId, BigDecimal quantity) {
        PlacementTask task = findTask(taskId);
        ReceiptItem item = findItem(task.getReceipt(), itemId);
        return buildPlacementItem(item, quantity == null ? item.getQuantity() : quantity);
    }

    /**
     * То же для решения ACCEPT_ACTUAL: проверяется фактическое размещение (фактическое количество, а не плановое).
     * Вызывается до транзакции проведения.
     */
    @Transactional(readOnly = true)
    public PlacementItem placementItemForPlaced(UUID taskId, UUID itemId) {
        PlacementTask task = findTask(taskId);
        ReceiptItem item = findItem(task.getReceipt(), itemId);
        if (item.getPlacedQuantity() == null) {
            throw ApiException.conflict("ITEM_NOT_IN_DISCREPANCY", "Позиция не в статусе расхождения");
        }
        return buildPlacementItem(item, item.getPlacedQuantity());
    }

    private PlacementItem buildPlacementItem(ReceiptItem item, BigDecimal quantity) {
        if (item.getSnapshotJson() == null) {
            throw ApiException.conflict("ITEM_NOT_PREPARED", "Позиция не подготовлена к размещению");
        }
        try {
            BatchSnapshot snapshot = jsonMapper.readValue(item.getSnapshotJson(), BatchSnapshot.class);
            return PlacementItemFactory.fromSnapshot(item, snapshot, quantity);
        } catch (JacksonException ex) {
            log.error("Повреждён snapshot_json позиции {}", item.getId(), ex);
            throw ApiException.conflict("ITEM_CORRUPTED", "Снимок позиции повреждён");
        }
    }

    /** Секция, в которую работник фактически разместил позицию в расхождении (для решения ACCEPT_ACTUAL). */
    @Transactional(readOnly = true)
    public UUID placedSectionId(UUID taskId, UUID itemId) {
        PlacementTask task = findTask(taskId);
        UUID id = findItem(task.getReceipt(), itemId).getPlacedSectionId();
        if (id == null) throw ApiException.conflict("ITEM_NOT_IN_DISCREPANCY", "Позиция не в статусе расхождения");
        return id;
    }

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
        SectionRules.requirePlaceable(section, item.getTargetDecision()); // B2: не BLOCKED/ARCHIVED, не "Нераспределено" без решения
        QuantityRules.require(item.getUnitCode(), item.isUnitAllowFractions(), quantity, "Фактическое количество");
        if (item.getPlacementStatus() != ReceiptItem.PlacementStatus.PENDING) {
            throw ApiException.conflict("ITEM_NOT_PENDING", "Позиция уже обработана: " + item.getPlacementStatus());
        }
        requireCapacity(section, item, quantity); // B2: вместимость под блокировкой секции

        item.setPlacedSectionId(section.id());
        item.setPlacedSectionCode(section.code());
        item.setPlacedQuantity(quantity);
        item.setPlacedBy(workerId);
        item.setPlacedAt(OffsetDateTime.now());
        // нужен при проведении, в том числе позже, при решении кладовщика (ACCEPT_ACTUAL)
        item.setPlacedUnassignedZone(section.unassignedZone());

        boolean exact = section.id().equals(item.getTargetSectionId())
                && quantity.compareTo(item.getQuantity()) == 0;
        if (exact) {
            post(r, item, workerId, comment, section);
        } else {
            // Расхождение: остаток НЕ меняется, решение за кладовщиком (разд. 10 ТЗ)
            if (comment == null || comment.isBlank()) {
                throw ApiException.validation("При расхождении с планом укажите комментарий");
            }
            item.setDiscrepancyComment(comment.trim());
            item.setPlacementStatus(ReceiptItem.PlacementStatus.DISCREPANCY);
            outbox.audit(SERVICE, "receipt.placement_discrepancy", "RECEIPT", r.getId(), workerId, details(
                    "number", r.getNumber(),
                    "line", item.getLineNo(),
                    "itemId", item.getId(),
                    "productSku", item.getProductSku(),
                    "productName", item.getProductName(),
                    "unitCode", item.getUnitCode(),
                    "plannedSection", item.getTargetSectionCode(),
                    "plannedQuantity", item.getQuantity().toPlainString(),
                    "actualSection", section.code(),
                    "actualQuantity", quantity.toPlainString(),
                    "comment", comment.trim()));
        }
        finishIfDone(r, task);
        return InventoryMapper.toTaskResponse(task);
    }

    /** Решение кладовщика по расхождению. newTarget нужен только для RETURN_TO_PLACEMENT с новой секцией. */
    @Transactional
    public PlacementTaskResponse resolve(UUID taskId, UUID itemId, ResolveRequest req,
                                         SectionInfo newTarget, SectionInfo placedSection, UUID storekeeperId) {
        PlacementTask task = findTask(taskId);
        Receipt r = lockAndCheck(task);
        ReceiptItem item = findItem(r, itemId);
        // B4: отклонить можно и непринятую позицию (товар повреждён или потерян, размещать нечего)
        boolean rejecting = req.decision() == InventoryDtos.ResolveDecision.REJECT_LINE;
        ReceiptItem.PlacementStatus st = item.getPlacementStatus();
        boolean allowed = st == ReceiptItem.PlacementStatus.DISCREPANCY
                || (rejecting && st == ReceiptItem.PlacementStatus.PENDING);
        if (!allowed) {
            throw ApiException.conflict(rejecting ? "ITEM_NOT_REJECTABLE" : "ITEM_NOT_IN_DISCREPANCY",
                    rejecting ? "Позицию в статусе " + st + " отклонить нельзя" : "Позиция не в статусе расхождения");
        }
        // S6 (вариант 2): расхождение закрывает не тот, кто размещал (для позиции без размещения placedBy = null)
        if (item.getPlacedBy() != null && item.getPlacedBy().equals(storekeeperId)) {
            throw ApiException.forbidden("SELF_DISCREPANCY_RESOLUTION",
                    "Расхождение по собственному размещению закрывает другой кладовщик");
        }
        String note = req.comment().trim();
        switch (req.decision()) {
            case ACCEPT_ACTUAL -> {
                // Между размещением работником и решением кладовщика секцию могли заблокировать, заархивировать
                // или изменить её правила: все проверки повторяются на актуальном SectionInfo (B2/B3).
                if (placedSection == null || !placedSection.id().equals(item.getPlacedSectionId())) {
                    throw ApiException.conflict("ITEM_CHANGED", "Размещение позиции изменилось, повторите операцию");
                }
                if (!placedSection.warehouseId().equals(r.getWarehouseId())) {
                    throw ApiException.validation("Секция принадлежит другому складу");
                }
                SectionRules.requirePlaceable(placedSection, item.getTargetDecision());
                requireCapacity(placedSection, item, item.getPlacedQuantity()); // место могли занять после расхождения
                post(r, item, storekeeperId, "Решение кладовщика: " + note, placedSection);
            }
            case REJECT_LINE -> {
                item.setPlacedSectionId(null);
                item.setPlacedSectionCode(null);
                item.setPlacedQuantity(null);
                item.setPlacedBy(null);
                item.setPlacedAt(null);
                item.setPlacedUnassignedZone(false);
                item.setDiscrepancyComment("Позиция отклонена (недостача или брак): " + note);
                item.setPlacementStatus(ReceiptItem.PlacementStatus.REJECTED);
                // остаток и движения не создаются: на склад ничего не пришло
            }
            case RETURN_TO_PLACEMENT -> {
                if (newTarget != null) {
                    if (!newTarget.warehouseId().equals(r.getWarehouseId())) {
                        throw ApiException.validation("Секция принадлежит другому складу");
                    }
                    item.setTargetSectionId(newTarget.id());
                    item.setTargetSectionCode(newTarget.code());
                    item.setTargetDecision(SectionRules.decisionForNewTarget(newTarget));
                }
                item.setPlacedSectionId(null);
                item.setPlacedSectionCode(null);
                item.setPlacedQuantity(null);
                item.setPlacedBy(null);
                item.setPlacedAt(null);
                item.setPlacedUnassignedZone(false);
                item.setDiscrepancyComment("Возврат в размещение: " + note);
                item.setPlacementStatus(ReceiptItem.PlacementStatus.PENDING);
            }
        }
        outbox.audit(SERVICE, "receipt.discrepancy_resolved", "RECEIPT", r.getId(), storekeeperId, details(
                "number", r.getNumber(),
                "line", item.getLineNo(),
                "itemId", item.getId(),
                "decision", req.decision().name(),
                "comment", note));
        finishIfDone(r, task);
        return InventoryMapper.toTaskResponse(task);
    }

    // ================= внутреннее =================

    /**
     * Вместимость секции (B2). Блокировка по секции сериализует конкурирующие размещения: второе увидит
     * остаток, уже проведённый первым. Занято = физический остаток + запланированное другими позициями.
     * Свойства секции (предел и единица) берутся из SectionInfo, полученного до транзакции;
     * соответствие типа, материала и размеров проверено в warehouse-service.
     */
    private void requireCapacity(SectionInfo section, ReceiptItem item, BigDecimal quantity) {
        if (section == null || section.unassignedZone() || section.constraints() == null
                || section.constraints().maxCapacity() == null) {
            return; // вместимость не ограничена
        }
        String unit = section.constraints().capacityUnitCode();
        if (unit == null || !unit.equalsIgnoreCase(item.getUnitCode())) {
            throw ApiException.conflict("SECTION_UNIT_MISMATCH", "Секция «" + section.code()
                    + "» ограничена в единице " + unit + ", позиция в " + item.getUnitCode());
        }
        lockSection(section.id());
        BigDecimal used = stockRepository.sectionUsed(section.id(), item.getUnitCode())
                .add(itemRepository.pendingForSection(section.id(), item.getUnitCode(), IN_PROGRESS,
                        ReceiptItem.PlacementStatus.PENDING, item.getId()));
        BigDecimal free = section.constraints().maxCapacity().subtract(used);
        if (free.compareTo(quantity) < 0) {
            throw ApiException.conflict("SECTION_CAPACITY_EXCEEDED", "В секции «" + section.code()
                    + "» недостаточно места: свободно " + free.max(BigDecimal.ZERO).toPlainString()
                    + " " + item.getUnitCode() + ", нужно " + quantity.toPlainString());
        }
    }

    /** Блокировка до конца транзакции; ключ - секция. Берётся после блокировки документа, цикла ожидания нет. */
    private void lockSection(UUID sectionId) {
        lockKey("section:" + sectionId);
    }

    private void lockKey(String key) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", key)
                .query().rowSet();
    }

    /** Партия + остаток + движение RECEIPT: всё в текущей транзакции. */
    private void post(Receipt r, ReceiptItem item, UUID performedBy, String comment, SectionInfo section) {
        BigDecimal quantity = item.getPlacedQuantity();
        OffsetDateTime now = OffsetDateTime.now();

        // S1: партия = (номенклатура, номер партии). Повторная поставка пополняет существующую партию и строку остатка.
        // Блокировка по ключу партии сериализует одновременное создание: ограничение уникальности не сработает.
        String batchNumber = item.effectiveBatchNumber();
        lockKey("batch:" + item.getProductId() + "|" + batchNumber);
        Batch batch;
        var existingBatch = batchLookup.find(item.getProductId(), batchNumber);
        if (existingBatch.isPresent()) {
            String mismatch = BatchLookup.mismatch(existingBatch.get(), item.getUnitCode(), item.getExpiryAt());
            if (mismatch != null) {
                throw ApiException.conflict("BATCH_MISMATCH", "Партия «" + batchNumber + "»: " + mismatch);
            }
            batch = batchRepository.findById(existingBatch.get().id())
                    .orElseThrow(() -> ApiException.conflict("BATCH_NOT_FOUND", "Партия не найдена: " + batchNumber));
        } else {
            batch = new Batch();
            batch.setProductId(item.getProductId());
            batch.setProductSku(item.getProductSku());
            batch.setProductName(item.getProductName());
            batch.setUnitCode(item.getUnitCode());
            batch.setUnitAllowFractions(item.isUnitAllowFractions());
            batch.setReceiptId(r.getId());
            batch.setBatchNumber(batchNumber);
            batch.setReceivedAt(r.getReceivedAt());
            batch.setExpiryAt(item.getExpiryAt());
            batch.setAttributesSnapshotJson(item.getSnapshotJson() == null ? "{}" : item.getSnapshotJson());
            batch = batchRepository.save(batch);
        }

        var existingStock = batchLookup.findStockId(item.getProductId(), item.getPlacedSectionId(), batch.getId());
        if (existingStock.isPresent()) {
            // строка остатка уже есть (та же партия в той же секции): добавляем под блокировкой строки
            UUID stockId = existingStock.get();
            Stock stock = stockRepository.lockAll(java.util.Set.of(stockId)).get(stockId);
            stock.setQuantity(stock.getQuantity().add(quantity));
        } else {
            Stock stock = new Stock();
            stock.setWarehouseId(r.getWarehouseId());
            stock.setProductId(item.getProductId());
            stock.setSectionId(item.getPlacedSectionId());
            stock.setSectionCode(item.getPlacedSectionCode());
            stock.setUnassignedZone(item.isPlacedUnassignedZone()); // из "Нераспределено" резервировать нельзя
            stock.setSectionBlocked(SectionRules.blocksReservation(section)); // статус секции на момент проведения
            stock.setBatch(batch);
            stock.setUnitCode(item.getUnitCode());
            stock.setQuantity(quantity);
            stockRepository.save(stock);
        }

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
        movementEvents.publish(movement, batch, r.getNumber(), details(
                "lineNo", item.getLineNo(),
                "supplierName", r.getSupplierName(),
                "externalDocumentNumber", r.getExternalDocumentNumber(),
                "recommendedSectionCode", item.getRecommendedSectionCode()));

        item.setBatchId(batch.getId());
        item.setPlacementStatus(ReceiptItem.PlacementStatus.PLACED);
        outbox.audit(SERVICE, "receipt.item_placed", "RECEIPT", r.getId(), performedBy, details(
                "number", r.getNumber(),
                "line", item.getLineNo(),
                "sku", item.getProductSku(),
                "quantity", quantity.toPlainString(),
                "section", item.getPlacedSectionCode()));
    }

    /** Все позиции закрыты (размещены или отклонены): задание завершено, поступление в DONE. */
    private void finishIfDone(Receipt r, PlacementTask task) {
        if (r.getItems().stream().allMatch(i -> i.getPlacementStatus() == ReceiptItem.PlacementStatus.PLACED
                || i.getPlacementStatus() == ReceiptItem.PlacementStatus.REJECTED)) {
            long rejected = r.getItems().stream()
                    .filter(i -> i.getPlacementStatus() == ReceiptItem.PlacementStatus.REJECTED).count();
            OffsetDateTime now = OffsetDateTime.now();
            task.setStatus(PlacementTask.Status.COMPLETED);
            task.setCompletedAt(now);
            r.setStatus(Receipt.Status.DONE);
            r.setCompletedAt(now);
            r.touch();
            outbox.audit(SERVICE, "receipt.completed", "RECEIPT", r.getId(), null, details(
                    "number", r.getNumber(),
                    "rejectedLines", rejected,
                    "placedLines", r.getItems().size() - rejected));
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
