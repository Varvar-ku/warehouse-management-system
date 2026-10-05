// ReservationController.java
package ru.warehouse.inventory.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.inventory.dto.OperationDtos.*;
import ru.warehouse.inventory.service.ReservationService;
import java.util.List;
import java.util.UUID;

/**
 * Резервы. Вызывается интерфейсом кладовщика и (с этапа 5) request-service с пересылкой JWT пользователя.
 * POST /reservations требует заголовок Idempotency-Key (проверяет фильтр).
 */
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STOREKEEPER')")
public class ReservationController {
    private final ReservationService reservationService;

    @PostMapping("/availability")
    public List<AvailabilityItem> availability(@Valid @RequestBody AvailabilityRequest request) {
        return reservationService.availability(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReserveResponse reserve(@Valid @RequestBody ReserveRequest request, @AuthenticationPrincipal Jwt jwt) {
        return reservationService.reserve(request, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/release")
    public ReleaseResponse release(@Valid @RequestBody ReleaseRequest request, @AuthenticationPrincipal Jwt jwt) {
        return reservationService.release(request, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping
    public List<ReservationResponse> list(@RequestParam UUID requestId) {
        return reservationService.list(requestId);
    }
}
