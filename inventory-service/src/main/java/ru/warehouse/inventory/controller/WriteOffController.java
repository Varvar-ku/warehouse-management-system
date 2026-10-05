// WriteOffController.java
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
import ru.warehouse.inventory.entity.WriteOff;
import ru.warehouse.inventory.service.WriteOffService;
import java.util.UUID;

/**
 * Списания. Просмотр: кладовщик и администратор. Оформление и проведение: кладовщик.
 * Подтверждение при необходимости: администратор. POST /write-offs/{id}/confirm требует Idempotency-Key.
 */
@RestController
@RequestMapping("/api/v1/write-offs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STOREKEEPER', 'ADMIN')")
public class WriteOffController {
    private final WriteOffService writeOffService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STOREKEEPER')")
    public WriteOffResponse create(@Valid @RequestBody CreateWriteOffRequest request, @AuthenticationPrincipal Jwt jwt) {
        return writeOffService.create(request, user(jwt));
    }

    @GetMapping
    public PageResponse<WriteOffResponse> search(@RequestParam(required = false) WriteOff.Status status,
                                                 @RequestParam(required = false) WriteOff.Reason reason,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return writeOffService.search(status, reason, page, size);
    }

    @GetMapping("/{id}")
    public WriteOffResponse get(@PathVariable UUID id) {
        return writeOffService.get(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public WriteOffResponse approve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return writeOffService.approve(id, user(jwt));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public WriteOffResponse confirm(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return writeOffService.confirm(id, user(jwt));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public WriteOffResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return writeOffService.cancel(id, request.reason(), user(jwt));
    }

    private static UUID user(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
