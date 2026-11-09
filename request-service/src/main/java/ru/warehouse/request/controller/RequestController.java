package ru.warehouse.request.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.request.dto.RequestDtos.*;
import ru.warehouse.request.entity.RequestStatus;
import ru.warehouse.request.service.Actors;
import ru.warehouse.request.service.RequestService;
import ru.warehouse.request.service.RequestUsageService;
import java.util.List;
import java.util.UUID;
import ru.warehouse.request.dto.PickingDtos.*;
import ru.warehouse.request.service.IssuingService;

/**
 * Заявки. USER работает со своими заявками, STOREKEEPER обрабатывает все (разд. 4 ТЗ).
 * ADMIN и WAREHOUSE_WORKER к заявкам доступа не имеют.
 */
@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class RequestController {
    private final RequestService requestService;
    private final IssuingService issuingService;
    private final RequestUsageService usageService;

    /** Открытые заявки по номенклатуре или складу (M4: проверка перед архивацией и отключением). */
    public record OpenRequestsUsage(long openRequests) {}

    @GetMapping("/usage")
    @PreAuthorize("hasAnyRole('STOREKEEPER', 'ADMIN')")
    public OpenRequestsUsage usage(@RequestParam(required = false) UUID productId,
                                   @RequestParam(required = false) UUID warehouseId) {
        return new OpenRequestsUsage(usageService.openRequests(productId, warehouseId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public RequestResponse create(@Valid @RequestBody CreateRequestDto dto, @AuthenticationPrincipal Jwt jwt) {
        return requestService.create(dto, Actors.from(jwt));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'STOREKEEPER', 'ADMIN')")
    public PageResponse<RequestSummary> search(@RequestParam(required = false) RequestStatus status,
                                               @RequestParam(required = false) UUID warehouseId,
                                               @RequestParam(required = false) UUID requesterId,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(defaultValue = "NEWEST") RequestSort sort,
                                               @AuthenticationPrincipal Jwt jwt) {
        return requestService.search(status, warehouseId, requesterId, q, page, size, sort, Actors.from(jwt));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'STOREKEEPER', 'ADMIN')")
    public RequestResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return requestService.get(id, Actors.from(jwt));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('USER', 'STOREKEEPER', 'ADMIN')")
    public List<HistoryResponse> history(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return requestService.history(id, Actors.from(jwt));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public RequestResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRequestDto dto,
                                  @AuthenticationPrincipal Jwt jwt) {
        return requestService.update(id, dto, Actors.from(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER')")
    public void deleteDraft(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requestService.deleteDraft(id, Actors.from(jwt));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('USER')")
    public RequestResponse submit(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return requestService.submit(id, Actors.from(jwt));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'STOREKEEPER')")
    public RequestResponse cancel(@PathVariable UUID id, @Valid @RequestBody ReasonDto dto,
                                  @AuthenticationPrincipal Jwt jwt) {
        return requestService.cancel(id, dto.reason(), Actors.from(jwt));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public RequestResponse review(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return requestService.review(id, Actors.from(jwt));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public RequestResponse reject(@PathVariable UUID id, @Valid @RequestBody ReasonDto dto,
                                  @AuthenticationPrincipal Jwt jwt) {
        return requestService.reject(id, dto.reason(), Actors.from(jwt));
    }

    @PostMapping("/{id}/reserve")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public RequestResponse reserve(@PathVariable UUID id, @Valid @RequestBody(required = false) ReserveDto dto,
                                   @AuthenticationPrincipal Jwt jwt) {
        return requestService.reserve(id, dto, Actors.from(jwt));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public RequestResponse close(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return requestService.close(id, Actors.from(jwt));
    }

    @PostMapping("/{id}/ready-for-issue")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public RequestResponse readyForIssue(@PathVariable UUID id, @Valid @RequestBody(required = false) ReadyDto dto,
                                         @AuthenticationPrincipal Jwt jwt) {
        return requestService.readyForIssue(id, dto == null ? null : dto.comment(), Actors.from(jwt));
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public RequestResponse issue(@PathVariable UUID id, @Valid @RequestBody(required = false) IssueRequestDto dto,
                                 @AuthenticationPrincipal Jwt jwt) {
        return issuingService.issue(id, dto, Actors.from(jwt));
    }
}