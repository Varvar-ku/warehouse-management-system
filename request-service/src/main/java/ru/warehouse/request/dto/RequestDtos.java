package ru.warehouse.request.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.warehouse.request.entity.RequestStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** В запросах только ссылочные типы (Integer, Boolean): Jackson 3 не принимает отсутствующее примитивное поле. */
public final class RequestDtos {
    private RequestDtos() {}

    // ================= запросы =================

    public record ItemInput(
            @NotNull UUID productId,
            @NotNull @Positive @Digits(integer = 14, fraction = 4) BigDecimal quantity,
            @Size(max = 1000) String comment) {}

    public record CreateRequestDto(
            @NotNull UUID warehouseId,
            @Min(0) @Max(9) Integer priority,          // 0 по умолчанию; больше = срочнее
            @Size(max = 2000) String comment,
            @NotEmpty @Size(max = 200) List<@Valid ItemInput> items) {}

    /** PATCH только для черновика: null = «не менять»; если передан items, позиции заменяются целиком. */
    public record UpdateRequestDto(
            @Min(0) @Max(9) Integer priority,
            @Size(max = 2000) String comment,
            @Size(max = 200) List<@Valid ItemInput> items) {}

    public record ItemApproval(
            @NotNull UUID itemId,
            @NotNull @PositiveOrZero @Digits(integer = 14, fraction = 4) BigDecimal approvedQuantity) {}

    /** Решение кладовщика при частичном обеспечении: какие количества резервировать (необязательно). */
    public record ReserveDto(List<@Valid ItemApproval> items) {}

    /** Порядок списка: NEWEST по умолчанию (новые сверху); PRIORITY для очереди кладовщика: срочные, затем раньше поданные. */
    public enum RequestSort { NEWEST, PRIORITY }

    public record ReasonDto(@NotBlank @Size(max = 1000) String reason) {}

    // ================= ответы =================

    public record RequestItemResponse(
            UUID id, int lineNo, UUID productId, String productSku, String productName, String unitCode,
            BigDecimal requestedQuantity, BigDecimal approvedQuantity, BigDecimal reservedQuantity,
            BigDecimal issuedQuantity, BigDecimal unavailableQuantity, String comment) {}

    public record RequestResponse(
            UUID id, String number, UUID requesterId, UUID warehouseId, RequestStatus status, int priority,
            String comment, String statusReason, OffsetDateTime createdAt, OffsetDateTime submittedAt,
            UUID reviewedBy, OffsetDateTime reviewedAt, OffsetDateTime closedAt, String issueNumber,
            List<RequestItemResponse> items) {}

    public record RequestSummary(UUID id, String number, UUID requesterId, UUID warehouseId, RequestStatus status,
                                 int priority, OffsetDateTime createdAt, OffsetDateTime submittedAt, int itemsCount) {}

    public record HistoryResponse(RequestStatus fromStatus, RequestStatus toStatus, UUID actorId,
                                  String comment, OffsetDateTime createdAt) {}
}
