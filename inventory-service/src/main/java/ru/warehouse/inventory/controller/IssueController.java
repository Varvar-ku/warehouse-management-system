// IssueController.java
package ru.warehouse.inventory.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.inventory.dto.InventoryDtos.CancelRequest;
import ru.warehouse.inventory.dto.OperationDtos.*;
import ru.warehouse.inventory.entity.Issue;
import ru.warehouse.inventory.service.IssueService;
import java.util.UUID;

/** Документы передачи: оформляет кладовщик (разд. 4 ТЗ). POST /issues/{id}/confirm требует Idempotency-Key. */
@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STOREKEEPER')")
public class IssueController {
    private final IssueService issueService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssueResponse create(@Valid @RequestBody CreateIssueRequest request, @AuthenticationPrincipal Jwt jwt) {
        return issueService.create(request, user(jwt));
    }

    @GetMapping
    public PageResponse<IssueResponse> search(@RequestParam(required = false) Issue.Status status,
                                              @RequestParam(required = false) UUID requestId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return issueService.search(status, requestId, page, size);
    }

    @GetMapping("/{id}")
    public IssueResponse get(@PathVariable UUID id) {
        return issueService.get(id);
    }

    @PostMapping("/{id}/confirm")
    public IssueResponse confirm(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return issueService.confirm(id, user(jwt));
    }

    @PostMapping("/{id}/cancel")
    public IssueResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return issueService.cancel(id, request.reason(), user(jwt));
    }

    private static UUID user(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
