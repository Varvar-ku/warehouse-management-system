// IssuingService.java
package ru.warehouse.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.request.client.InventoryClient;
import ru.warehouse.request.client.InventoryContract.CreateIssueRequest;
import ru.warehouse.request.client.InventoryContract.IssueDto;
import ru.warehouse.request.client.InventoryContract.IssueLine;
import ru.warehouse.request.client.InventoryContract.ShortageMark;
import ru.warehouse.request.dto.PickingDtos.IssueRequestDto;
import ru.warehouse.request.dto.RequestDtos.RequestResponse;
import ru.warehouse.request.entity.PickingTask;
import ru.warehouse.request.entity.Request;
import ru.warehouse.request.entity.RequestItem;
import ru.warehouse.request.entity.RequestStatus;
import ru.warehouse.request.repository.PickingTaskRepository;
import ru.warehouse.request.repository.RequestRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import ru.warehouse.common.event.OutboxPublisher;
import static ru.warehouse.common.event.OutboxPublisher.details;
import ru.warehouse.request.entity.PickingItem;

/**
 * Оформление выдачи (разд. 13 ТЗ). Остаток меняет inventory-service одной транзакцией (документ передачи,
 * резерв → CONSUMED, остаток, движения ISSUE); здесь фиксируется результат: ISSUED, затем CLOSED.
 * Шаги идемпотентны: после сбоя повторный вызов подхватывает уже существующий документ inventory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssuingService {
    private final RequestRepository requestRepository;
    private final PickingTaskRepository taskRepository;
    private final InventoryClient inventoryClient;
    private final RequestService requestService;
    private static final String SERVICE = "request-service";
    private final OutboxPublisher outbox;
    private final RequestOperations ops;
    /** Аренда операции: до четырёх вызовов inventory (по 2+10 с). */
    private static final Duration OPERATION_TTL = Duration.ofSeconds(120);

    /** Причина недовыдачи: комментарии работников по недобору, иначе нехватка резерва, иначе не указана. */
    private String discrepancyReason(RequestItem item, List<PickingTask> tasks) {
        String shortages = tasks.stream().flatMap(t -> t.getItems().stream())
                .filter(i -> i.getRequestItem().getId().equals(item.getId())
                        && i.getStatus() == PickingItem.Status.SHORTAGE && i.getComment() != null)
                .map(PickingItem::getComment).distinct().collect(Collectors.joining("; "));
        if (!shortages.isEmpty()) {
            return shortages;
        }
        if (item.getReservedQuantity() != null
                && item.getReservedQuantity().compareTo(item.getRequestedQuantity()) < 0) {
            return "Зарезервировано меньше запрошенного";
        }
        return null;
    }

    /**
     * Недобор по резервам: сумма (назначено минус собрано) по строкам со статусом SHORTAGE.
     * Строка задания может быть частью резерва (после распределения между работниками), поэтому суммируем по резерву.
     */
    static List<ShortageMark> shortageMarks(List<PickingTask> tasks) {
        Map<UUID, BigDecimal> byReservation = new LinkedHashMap<>();
        for (PickingTask t : tasks) {
            for (PickingItem row : t.getItems()) {
                if (row.getStatus() != PickingItem.Status.SHORTAGE) continue;
                BigDecimal picked = row.getPickedQuantity() == null ? BigDecimal.ZERO : row.getPickedQuantity();
                BigDecimal missing = row.getRequestedQuantity().subtract(picked);
                if (missing.signum() > 0) byReservation.merge(row.getReservationId(), missing, BigDecimal::add);
            }
        }
        return byReservation.entrySet().stream().map(e -> new ShortageMark(e.getKey(), e.getValue())).toList();
    }

    private record Plan(String number, UUID warehouseId, UUID recipient, String comment,
                        Map<UUID, BigDecimal> picked, List<IssueLine> lines, List<ShortageMark> marks) {}

    /**
     * S2: вызовы inventory (до четырёх) идут между двумя короткими транзакциями, а не под блокировкой заявки.
     * Все шаги в inventory идемпотентны или восстановимы (подхват подтверждённого документа), поэтому повтор
     * после сбоя безопасен.
     */
    public RequestResponse issue(UUID id, IssueRequestDto dto, Actor actor) {
        return ops.<Plan, IssueDto, RequestResponse>run(id, OPERATION_TTL,
                r -> prepare(r, dto),
                plan -> callInventory(id, plan),
                (r, plan, issue) -> finish(r, plan, issue, actor));
    }

    private Plan prepare(Request r, IssueRequestDto dto) {
        if (r.getStatus() != RequestStatus.READY_FOR_ISSUE) {
            throw ApiException.conflict("INVALID_STATUS", "Выдача оформляется для заявки READY_FOR_ISSUE, статус: " + r.getStatus());
        }
        UUID recipient = dto != null && dto.issuedTo() != null ? dto.issuedTo() : r.getRequesterId();
        String comment = dto == null || dto.comment() == null || dto.comment().isBlank() ? null : dto.comment().trim();

        // Фактически собранное по позициям заявки: только это попадает в документ передачи
        Map<UUID, BigDecimal> picked = new HashMap<>();
        for (PickingTaskRepository.PickedTotal t : taskRepository.pickedTotals(r.getId(), PickingTask.Status.COMPLETED)) {
            picked.put(t.getRequestItemId(), t.getPicked());
        }
        // Завершённые задания: из них берём недобор по резервам (откуда именно не собрано)
        List<ShortageMark> marks = shortageMarks(taskRepository.findByRequestIdAndStatusIn(r.getId(),
                List.of(PickingTask.Status.COMPLETED)));
        List<IssueLine> lines = r.getItems().stream()
                .filter(i -> picked.getOrDefault(i.getId(), BigDecimal.ZERO).signum() > 0)
                .map(i -> new IssueLine(i.getId(), i.getProductId(), picked.get(i.getId()))).toList();
        return new Plan(r.getNumber(), r.getWarehouseId(), recipient, comment, picked, lines, marks);
    }

    /** Шаги в inventory. Подтверждённый документ подхватывается; оставшийся черновик отменяется и создаётся заново (S5). */
    private IssueDto callInventory(UUID requestId, Plan plan) {
        List<IssueDto> existing = inventoryClient.issues(requestId);
        IssueDto confirmed = existing.stream().filter(i -> "CONFIRMED".equals(i.status())).findFirst().orElse(null);
        if (confirmed != null) {
            log.warn("Выдача по заявке {} уже подтверждена в inventory ({}): подхвачена без повторного проведения",
                    plan.number(), confirmed.number());
            return confirmed;
        }
        if (plan.lines().isEmpty()) {
            throw ApiException.conflict("NOTHING_TO_ISSUE", "Ничего не собрано: выдавать нечего, отмените заявку");
        }
        // Черновик остаток не меняет, поэтому пересоздание безопасно: учитываются получатель и комментарий
        // текущего запроса, а «зависший» черновик (например, после RESERVATION_CHANGED) не блокирует выдачу.
        for (IssueDto stale : existing) {
            if ("DRAFT".equals(stale.status())) {
                log.warn("По заявке {} найден незавершённый черновик {}: отменяется и создаётся заново",
                        plan.number(), stale.number());
                inventoryClient.cancelIssue(stale.id(), "Пересоздан при повторном оформлении выдачи");
            }
        }
        IssueDto draft = inventoryClient.createIssue(new CreateIssueRequest(requestId, plan.warehouseId(), plan.recipient(),
                plan.comment(), plan.lines(), plan.marks().isEmpty() ? null : plan.marks()));
        return inventoryClient.confirmIssue(draft.id());
    }

    private RequestResponse finish(Request r, Plan plan, IssueDto issue, Actor actor) {
        if (r.getStatus() != RequestStatus.READY_FOR_ISSUE) {
            throw ApiException.conflict("INVALID_STATUS", "Статус заявки изменился во время выдачи: " + r.getStatus());
        }
        for (RequestItem item : r.getItems()) {
            item.setIssuedQuantity(plan.picked().getOrDefault(item.getId(), BigDecimal.ZERO));
        }
        r.setIssueId(issue.id());
        r.setIssueNumber(issue.number());
        requestService.transition(r, RequestStatus.ISSUED, actor.userId(), "Документ передачи " + issue.number());
        requestService.transition(r, RequestStatus.CLOSED, actor.userId(), "Закрыта автоматически после выдачи");
        r.setClosedAt(OffsetDateTime.now());
        log.info("AUDIT: выдана заявка {} по документу {}, получатель {}, кладовщик {}",
                r.getNumber(), issue.number(), plan.recipient(), actor.userId());
        List<PickingTask> doneTasks = taskRepository.findByRequestIdAndStatusIn(r.getId(),
                List.of(PickingTask.Status.COMPLETED));
        for (RequestItem item : r.getItems()) {
            if (item.getIssuedQuantity().compareTo(item.getRequestedQuantity()) == 0) {
                continue; // выдано ровно столько, сколько запрошено: расхождения нет
            }
            outbox.audit(SERVICE, "request.discrepancy", "REQUEST", r.getId(), actor.userId(), details(
                    "requestNumber", r.getNumber(),
                    "issueNumber", issue.number(),
                    "requestItemId", item.getId(),
                    "lineNo", item.getLineNo(),
                    "productSku", item.getProductSku(),
                    "productName", item.getProductName(),
                    "unitCode", item.getUnitCode(),
                    "requestedQuantity", item.getRequestedQuantity().toPlainString(),
                    "issuedQuantity", item.getIssuedQuantity().toPlainString(),
                    "reason", discrepancyReason(item, doneTasks)));
        }
        return RequestMapper.toResponse(r);
    }
}
