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
import ru.warehouse.request.service.Actor;
import ru.warehouse.request.service.RequestService;
import java.util.List;
import java.util.UUID;

/**
 * Заявки. USER работает со своими заявками, STOREKEEPER обрабатывает все (разд. 4 ТЗ).
 * ADMIN и WAREHOUSE_WORKER к заявкам доступа не имеют.
 */
@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class RequestController {
    private final RequestService requestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public RequestResponse create(@Valid @RequestBody CreateRequestDto dto, @AuthenticationPrincipal Jwt jwt) {
        return requestService.create(dto, actor(jwt));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'STOREKEEPER')")
    public PageResponse<RequestSummary> search(@RequestParam(required = false) RequestStatus status,
                                               @RequestParam(required = false) UUID warehouseId,
                                               @RequestParam(required = false) UUID requesterId,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @AuthenticationPrincipal Jwt jwt) {
        return requestService.search(status, warehouseId, requesterId, q, page, size, actor(jwt));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'STOREKEEPER')")
    public RequestResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return requestService.get(id, actor(jwt));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('USER', 'STOREKEEPER')")
    public List<HistoryResponse> history(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return requestService.history(id, actor(jwt));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public RequestResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRequestDto dto,
                                  @AuthenticationPrincipal Jwt jwt) {
        return requestService.update(id, dto, actor(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER')")
    public void deleteDraft(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requestService.deleteDraft(id, actor(jwt));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('USER')")
    public RequestResponse submit(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return requestService.submit(id, actor(jwt));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'STOREKEEPER')")
    public RequestResponse cancel(@PathVariable UUID id, @Valid @RequestBody ReasonDto dto,
                                  @AuthenticationPrincipal Jwt jwt) {
        return requestService.cancel(id, dto.reason(), actor(jwt));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public RequestResponse review(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return requestService.review(id, actor(jwt));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public RequestResponse reject(@PathVariable UUID id, @Valid @RequestBody ReasonDto dto,
                                  @AuthenticationPrincipal Jwt jwt) {
        return requestService.reject(id, dto.reason(), actor(jwt));
    }

    @PostMapping("/{id}/reserve")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public RequestResponse reserve(@PathVariable UUID id, @Valid @RequestBody(required = false) ReserveDto dto,
                                   @AuthenticationPrincipal Jwt jwt) {
        return requestService.reserve(id, dto, actor(jwt));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public RequestResponse close(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return requestService.close(id, actor(jwt));
    }

    private static Actor actor(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new Actor(UUID.fromString(jwt.getSubject()), roles != null && roles.contains("STOREKEEPER"));
    }
}