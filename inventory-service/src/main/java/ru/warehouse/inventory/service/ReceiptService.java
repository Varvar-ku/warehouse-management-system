// ReceiptService.java
package ru.warehouse.inventory.service;

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
import ru.warehouse.inventory.client.CatalogClient;
import ru.warehouse.inventory.client.CatalogContract.CatalogAttribute;
import ru.warehouse.inventory.client.CatalogContract.CatalogProduct;
import ru.warehouse.inventory.client.WarehouseClient;
import ru.warehouse.inventory.client.WarehouseContract.*;
import ru.warehouse.inventory.dto.InventoryDtos.*;
import ru.warehouse.inventory.entity.PlacementTask;
import ru.warehouse.inventory.entity.Receipt;
import ru.warehouse.inventory.entity.ReceiptItem;
import ru.warehouse.inventory.repository.BatchRepository;
import ru.warehouse.inventory.repository.PlacementTaskRepository;
import ru.warehouse.inventory.repository.ReceiptRepository;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Документы поступления: создание, подбор секции, подтверждение приёмки, задание на размещение, отмена. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {
    private final ReceiptRepository receiptRepository;
    private final PlacementTaskRepository taskRepository;
    private final BatchRepository batchRepository;
    private final CatalogClient catalogClient;
    private final WarehouseClient warehouseClient;
    private final OccupancyService occupancyService;
    private final JsonMapper jsonMapper;

    // ================= создание и правка =================

    @Transactional
    public ReceiptResponse create(CreateReceiptRequest req, UUID actorId) {
        WarehouseInfo warehouse = warehouseClient.warehouse(req.warehouseId());
        if (!warehouse.active()) {
            throw ApiException.conflict("WAREHOUSE_INACTIVE", "Склад отключён");
        }
        Receipt r = new Receipt();
        r.setNumber(String.format("RC-%d-%06d", OffsetDateTime.now().getYear(), receiptRepository.nextNumber()));
        r.setWarehouseId(warehouse.id());
        r.setSupplierName(req.supplierName().trim());
        r.setExternalDocumentNumber(blankToNull(req.externalDocumentNumber()));
        r.setReceivedAt(req.receivedAt() == null ? OffsetDateTime.now() : req.receivedAt());
        r.setCreatedBy(actorId);
        fillItems(r, req.items());
        r = receiptRepository.save(r);
        log.info("AUDIT: создано поступление {} ({}), инициатор {}", r.getNumber(), r.getId(), actorId);
        return InventoryMapper.toResponse(r);
    }

    @Transactional
    public ReceiptResponse update(UUID id, UpdateReceiptRequest req) {
        Receipt r = find(id);
        requireStatus(r, Receipt.Status.DRAFT);
        if (req.supplierName() != null) r.setSupplierName(req.supplierName().trim());
        if (req.externalDocumentNumber() != null) r.setExternalDocumentNumber(blankToNull(req.externalDocumentNumber()));
        if (req.receivedAt() != null) r.setReceivedAt(req.receivedAt());
        if (req.items() != null) {
            if (req.items().isEmpty()) throw ApiException.validation("Список позиций не может быть пустым");
            fillItems(r, req.items()); // новые позиции: прежние рекомендации сбрасываются
        }
        r.touch();
        return InventoryMapper.toResponse(r);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceiptSummary> search(Receipt.Status status, UUID warehouseId, String text, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Specification<Receipt> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (warehouseId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("warehouseId"), warehouseId));
        if (text != null && !text.isBlank()) {
            String escaped = text.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String pattern = "%" + escaped + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("number")), pattern, '\\'),
                    cb.like(cb.lower(root.get("supplierName")), pattern, '\\'),
                    cb.like(cb.lower(root.get("externalDocumentNumber")), pattern, '\\')));
        }
        return PageResponse.from(receiptRepository.findAll(spec, pageable).map(InventoryMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse get(UUID id) {
        return InventoryMapper.toResponse(find(id));
    }

    // ================= подбор секции =================

    /** Предварительный расчёт (разд. 10 ТЗ, шаг 4). Рекомендация и её основание сохраняются в позициях. */
    @Transactional
    public List<ItemRecommendationResponse> recommend(UUID id) {
        Receipt r = find(id);
        requireStatus(r, Receipt.Status.DRAFT);
        Map<UUID, CatalogProduct> products = loadProducts(productIds(r));
        Map<UUID, PlacementResult> results = recommendFor(r, products);
        r.touch();
        return r.getItems().stream().map(i -> InventoryMapper.toRecommendation(i, results.get(i.getId()))).toList();
    }

    // ================= подтверждение приёмки =================

    /**
     * Подтверждение приёмки: свежий расчёт (занятость могла измениться), проверка решений кладовщика,
     * фиксация целевых секций и основания выбора. Документ переходит в RECEIVING.
     */
    @Transactional
    public ReceiptResponse confirm(UUID id, ConfirmReceiptRequest req, UUID actorId) {
        Receipt r = find(id);
        requireStatus(r, Receipt.Status.DRAFT);
        Map<UUID, UUID> decisions = new HashMap<>();
        if (req != null && req.decisions() != null) {
            for (ItemDecision d : req.decisions()) {
                if (decisions.put(d.itemId(), d.targetSectionId()) != null) {
                    throw ApiException.validation("Решение по позиции указано дважды: " + d.itemId());
                }
            }
        }
        Set<UUID> itemIds = r.getItems().stream().map(ReceiptItem::getId).collect(Collectors.toSet());
        if (!itemIds.containsAll(decisions.keySet())) {
            throw ApiException.validation("В решениях указана позиция, которой нет в документе");
        }

        Map<UUID, CatalogProduct> products = loadProducts(productIds(r));
        Map<UUID, PlacementResult> results = recommendFor(r, products);

        List<String> problems = new ArrayList<>();
        Set<String> batchKeys = new HashSet<>();
        for (ReceiptItem item : r.getItems()) {
            String where = "Позиция " + item.getLineNo() + " (" + item.getProductSku() + "): ";
            PlacementResult pr = results.get(item.getId());
            boolean assigned = "ASSIGNED".equals(pr.status());
            UUID chosen = decisions.get(item.getId());

            if (chosen == null) {
                if (!assigned) {
                    problems.add(where + "подходящей секции нет, нужно решение кладовщика (укажите секцию из допустимых "
                            + "или подтвердите зону «Нераспределено»: sectionId " + pr.sectionId() + ")");
                    continue;
                }
                setTarget(item, pr.sectionId(), pr.sectionCode(), ReceiptItem.TargetDecision.RECOMMENDED);
            } else if (chosen.equals(pr.sectionId())) {
                setTarget(item, chosen, pr.sectionCode(), assigned
                        ? ReceiptItem.TargetDecision.RECOMMENDED : ReceiptItem.TargetDecision.UNASSIGNED_ZONE);
            } else {
                List<Candidate> candidates = pr.candidates() == null ? List.of() : pr.candidates();
                Optional<Candidate> alternative = candidates.stream()
                        .filter(c -> c.sectionId().equals(chosen)).findFirst();
                if (alternative.isEmpty()) {
                    problems.add(where + "выбранная секция не входит в список допустимых");
                    continue;
                }
                setTarget(item, chosen, alternative.get().sectionCode(), ReceiptItem.TargetDecision.ALTERNATIVE);
            }

            // Номер партии должен быть свободен: иначе проведение упрётся в ограничение уже после физического размещения
            String batchNumber = item.effectiveBatchNumber();
            if (!batchKeys.add(item.getProductId() + "|" + batchNumber)
                    || batchRepository.existsByProductIdAndBatchNumber(item.getProductId(), batchNumber)) {
                problems.add(where + "номер партии «" + batchNumber + "» уже используется");
                continue;
            }
            CatalogProduct p = products.get(item.getProductId());
            item.setSnapshotJson(jsonMapper.writeValueAsString(new BatchSnapshot(p.sku(), p.name(),
                    p.productTypeCode(), p.unitCode(), p.attributes(), readCharacteristics(item))));
            item.setPlacementStatus(ReceiptItem.PlacementStatus.PENDING);
        }
        if (!problems.isEmpty()) {
            throw ApiException.conflict("CONFIRMATION_BLOCKED", "Приёмку нельзя подтвердить", problems);
        }

        r.setStatus(Receipt.Status.RECEIVING);
        r.setConfirmedAt(OffsetDateTime.now());
        r.setConfirmedBy(actorId);
        r.touch();
        log.info("AUDIT: подтверждена приёмка {} ({}), инициатор {}", r.getNumber(), r.getId(), actorId);
        return InventoryMapper.toResponse(r);
    }

    // ================= задание на размещение и отмена =================

    @Transactional
    public PlacementTaskResponse createPlacementTask(UUID receiptId, UUID assigneeId, UUID actorId) {
        Receipt r = receiptRepository.findByIdForUpdate(receiptId)
                .orElseThrow(() -> ApiException.notFound("RECEIPT_NOT_FOUND", "Поступление не найдено"));
        requireStatus(r, Receipt.Status.RECEIVING);
        if (r.getItems().stream().anyMatch(i -> i.getPlacementStatus() != ReceiptItem.PlacementStatus.PENDING)) {
            throw ApiException.conflict("ITEMS_NOT_READY", "Не все позиции подготовлены к размещению");
        }
        if (taskRepository.existsByReceiptId(receiptId)) {
            throw ApiException.conflict("TASK_EXISTS", "Задание на размещение уже создано");
        }
        PlacementTask task = new PlacementTask();
        task.setReceipt(r);
        task.setAssigneeId(assigneeId);
        task.setCreatedBy(actorId);
        task = taskRepository.save(task);
        r.setStatus(Receipt.Status.PLACEMENT);
        r.touch();
        log.info("AUDIT: создано задание на размещение {} по поступлению {}, исполнитель {}, инициатор {}",
                task.getId(), r.getNumber(), assigneeId, actorId);
        return InventoryMapper.toTaskResponse(task);
    }

    @Transactional
    public ReceiptResponse cancel(UUID id, String reason, UUID actorId) {
        Receipt r = receiptRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("RECEIPT_NOT_FOUND", "Поступление не найдено"));
        if (r.getStatus() == Receipt.Status.DONE || r.getStatus() == Receipt.Status.CANCELLED) {
            throw ApiException.conflict("INVALID_STATUS", "Документ в статусе " + r.getStatus() + " отменить нельзя");
        }
        if (r.getItems().stream().anyMatch(i -> i.getPlacementStatus() == ReceiptItem.PlacementStatus.PLACED)) {
            throw ApiException.conflict("PARTIALLY_PLACED",
                    "Часть позиций уже размещена и проведена по остатку: отмена невозможна");
        }
        r.setStatus(Receipt.Status.CANCELLED);
        r.setCancelReason(reason.trim());
        r.setCancelledBy(actorId);
        r.touch();
        taskRepository.findByReceiptId(id).ifPresent(t -> t.setStatus(PlacementTask.Status.CANCELLED));
        log.info("AUDIT: отменено поступление {} ({}), причина: {}, инициатор {}", r.getNumber(), r.getId(), reason, actorId);
        return InventoryMapper.toResponse(r);
    }

    // ================= вспомогательные методы =================

    private Receipt find(UUID id) {
        return receiptRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("RECEIPT_NOT_FOUND", "Поступление не найдено"));
    }

    private static void requireStatus(Receipt r, Receipt.Status expected) {
        if (r.getStatus() != expected) {
            throw ApiException.conflict("INVALID_STATUS",
                    "Операция допустима для статуса " + expected + ", текущий статус: " + r.getStatus());
        }
    }

    private static Set<UUID> productIds(Receipt r) {
        return r.getItems().stream().map(ReceiptItem::getProductId).collect(Collectors.toSet());
    }

    private Map<UUID, CatalogProduct> loadProducts(Set<UUID> ids) {
        Map<UUID, CatalogProduct> found = catalogClient.products(ids);
        for (UUID id : ids) {
            CatalogProduct p = found.get(id);
            if (p == null) throw ApiException.validation("Номенклатура не найдена: " + id);
            if (!p.active()) throw ApiException.validation("Номенклатура в архиве: " + p.sku());
        }
        return found;
    }

    /** Заменяет позиции документа. Сначала удаляем старые и сбрасываем в БД: иначе INSERT нового line_no опередит DELETE. */
    private void fillItems(Receipt r, List<ReceiptItemRequest> requests) {
        Map<UUID, CatalogProduct> products = loadProducts(
                requests.stream().map(ReceiptItemRequest::productId).collect(Collectors.toSet()));
        r.getItems().clear();
        if (r.getId() != null) receiptRepository.flush();

        Set<String> batchKeys = new HashSet<>();
        int line = 1;
        for (ReceiptItemRequest req : requests) {
            CatalogProduct p = products.get(req.productId());
            ReceiptItem item = new ReceiptItem();
            item.setReceipt(r);
            item.setLineNo(line++);
            item.setProductId(p.id());
            item.setProductSku(p.sku());
            item.setProductName(p.name());
            item.setProductTypeCode(p.productTypeCode());
            item.setUnitCode(p.unitCode());
            boolean allowFractions = p.unitAllowFractions() == null || p.unitAllowFractions();
            QuantityRules.require(p.unitCode(), allowFractions, req.quantity(), "Позиция " + item.getLineNo());
            item.setUnitAllowFractions(allowFractions);
            item.setQuantity(req.quantity());
            String batchNumber = blankToNull(req.batchNumber());
            if (batchNumber != null && !batchKeys.add(p.id() + "|" + batchNumber)) {
                throw ApiException.validation("Номер партии повторяется в документе: " + batchNumber);
            }
            item.setBatchNumber(batchNumber);
            item.setExpiryAt(req.expiryAt());
            item.setComment(blankToNull(req.comment()));
            item.setCharacteristicsJson(req.characteristics() == null ? "{}"
                    : jsonMapper.writeValueAsString(normalize(req.characteristics())));
            item.setPlacementStatus(ReceiptItem.PlacementStatus.NEW);
            r.getItems().add(item);
        }
    }

    private static CharacteristicsDto normalize(CharacteristicsDto c) {
        Map<String, NumericValueDto> numeric = new TreeMap<>();
        if (c.numeric() != null) {
            c.numeric().forEach((k, v) -> numeric.put(k.trim().toUpperCase(Locale.ROOT), v));
        }
        String material = c.material() == null || c.material().isBlank() ? null : c.material().trim().toUpperCase(Locale.ROOT);
        return new CharacteristicsDto(material, numeric);
    }

    private CharacteristicsDto readCharacteristics(ReceiptItem item) {
        try {
            return jsonMapper.readValue(item.getCharacteristicsJson(), CharacteristicsDto.class);
        } catch (JacksonException e) {
            log.error("Повреждён characteristics_json позиции {}", item.getId(), e);
            throw ApiException.conflict("ITEM_CORRUPTED", "Характеристики позиции повреждены");
        }
    }

    /** Расчёт в warehouse-service и запись рекомендации (с основанием выбора) в позиции. */
    private Map<UUID, PlacementResult> recommendFor(Receipt r, Map<UUID, CatalogProduct> products) {
        List<PlacementItem> items = r.getItems().stream()
                .map(i -> toPlacementItem(i, products.get(i.getProductId()))).toList();
        List<Occupancy> occupancy = occupancyService.forWarehouse(r.getWarehouseId(), r.getId());
        List<PlacementResult> results = warehouseClient.recommend(new PlacementRequest(r.getWarehouseId(), items, occupancy));

        Map<UUID, PlacementResult> byItem = new HashMap<>();
        for (PlacementResult pr : results) byItem.put(UUID.fromString(pr.lineId()), pr);
        for (ReceiptItem item : r.getItems()) {
            PlacementResult pr = byItem.get(item.getId());
            if (pr == null) {
                throw ApiException.upstream("warehouse-service не вернул результат по позиции " + item.getLineNo());
            }
            item.setRecommendedSectionId(pr.sectionId());
            item.setRecommendedSectionCode(pr.sectionCode());
            item.setRecommendationStrategy(pr.strategy());
            item.setRuleId(pr.ruleId());
            item.setRuleVersion(pr.ruleVersion());
            item.setRuleName(pr.ruleName());
            item.setRecommendationCalculatedAt(pr.calculatedAt());
            item.setRecommendationNotes(pr.notes() == null || pr.notes().isEmpty() ? null : String.join("\n", pr.notes()));
        }
        return byItem;
    }

    /** Характеристики для подбора: данные каталога, поверх них уточнения кладовщика. */
    private PlacementItem toPlacementItem(ReceiptItem item, CatalogProduct product) {
        CharacteristicsDto overrides = readCharacteristics(item);
        String material = overrides.material();
        Map<String, NumericValue> numeric = new HashMap<>();
        if (product.attributes() != null) {
            for (CatalogAttribute a : product.attributes()) {
                if (a.valueNumber() != null) {
                    numeric.put(a.attributeCode(), new NumericValue(a.valueNumber(), a.valueUnitCode()));
                }
                if (material == null && "MATERIAL".equals(a.attributeCode()) && a.valueString() != null) {
                    material = a.valueString();
                }
            }
        }
        if (overrides.numeric() != null) {
            overrides.numeric().forEach((code, v) -> numeric.put(code, new NumericValue(v.value(), v.unitCode())));
        }
        return new PlacementItem(item.getId().toString(), item.getProductId(), product.productTypeCode(),
                material, numeric, item.getQuantity(), product.unitCode());
    }

    private static void setTarget(ReceiptItem item, UUID sectionId, String sectionCode, ReceiptItem.TargetDecision decision) {
        item.setTargetSectionId(sectionId);
        item.setTargetSectionCode(sectionCode);
        item.setTargetDecision(decision);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}