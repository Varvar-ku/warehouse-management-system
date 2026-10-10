// IssuingService.java
package ru.warehouse.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.request.client.InventoryClient;
import ru.warehouse.request.client.InventoryContract.CreateIssueRequest;
import ru.warehouse.request.client.InventoryContract.IssueDto;
import ru.warehouse.request.client.InventoryContract.IssueLine;
import ru.warehouse.request.dto.PickingDtos.IssueRequestDto;
import ru.warehouse.request.dto.RequestDtos.RequestResponse;
import ru.warehouse.request.entity.PickingTask;
import ru.warehouse.request.entity.Request;
import ru.warehouse.request.entity.RequestItem;
import ru.warehouse.request.entity.RequestStatus;
import ru.warehouse.request.repository.PickingTaskRepository;
import ru.warehouse.request.repository.RequestRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    @Transactional
    public RequestResponse issue(UUID id, IssueRequestDto dto, Actor actor) {
        Request r = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Заявка не найдена"));
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

        // 1. Что уже сделано в inventory (восстановление после сбоя): подтверждённый документ, затем черновик
        List<IssueDto> existing = inventoryClient.issues(r.getId());
        IssueDto issue = existing.stream().filter(i -> "CONFIRMED".equals(i.status())).findFirst().orElse(null);
        if (issue != null) {
            log.warn("Выдача по заявке {} уже подтверждена в inventory ({}): подхвачена без повторного проведения",
                    r.getNumber(), issue.number());
        } else {
            IssueDto draft = existing.stream().filter(i -> "DRAFT".equals(i.status())).findFirst().orElse(null);
            if (draft == null) {
                List<IssueLine> lines = r.getItems().stream()
                        .filter(i -> picked.getOrDefault(i.getId(), BigDecimal.ZERO).signum() > 0)
                        .map(i -> new IssueLine(i.getId(), i.getProductId(), picked.get(i.getId()))).toList();
                if (lines.isEmpty()) {
                    throw ApiException.conflict("NOTHING_TO_ISSUE", "Ничего не собрано: выдавать нечего, отмените заявку");
                }
                draft = inventoryClient.createIssue(new CreateIssueRequest(r.getId(), r.getWarehouseId(), recipient, comment, lines));
            }
            issue = inventoryClient.confirmIssue(draft.id());
        }

        // 2. Фиксируем результат у себя
        for (RequestItem item : r.getItems()) {
            item.setIssuedQuantity(picked.getOrDefault(item.getId(), BigDecimal.ZERO));
        }
        r.setIssueId(issue.id());
        r.setIssueNumber(issue.number());
        requestService.transition(r, RequestStatus.ISSUED, actor.userId(), "Документ передачи " + issue.number());
        requestService.transition(r, RequestStatus.CLOSED, actor.userId(), "Закрыта автоматически после выдачи");
        r.setClosedAt(OffsetDateTime.now());
        log.info("AUDIT: выдана заявка {} по документу {}, получатель {}, кладовщик {}",
                r.getNumber(), issue.number(), recipient, actor.userId());
        return RequestMapper.toResponse(r);
    }
}
