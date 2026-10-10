package ru.warehouse.request.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.request.dto.PickingDtos.*;
import ru.warehouse.request.entity.PickingTask;
import ru.warehouse.request.service.Actor;
import ru.warehouse.request.service.PickingService;
import java.util.List;
import java.util.UUID;

/**
 * Комплектация. Кладовщик создаёт задание и распределяет работу, работник склада собирает свои строки
 * (разд. 4, 12 и 28 ТЗ). Работник видит только свои строки и общий прогресс.
 */
@RestController
@RequestMapping("/api/v1/picking-tasks")
@RequiredArgsConstructor
public class PickingTaskController {
    private final PickingService pickingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STOREKEEPER')")
    public PickingTaskResponse create(@Valid @RequestBody CreatePickingTaskDto dto, @AuthenticationPrincipal Jwt jwt) {
        return pickingService.create(dto, actor(jwt));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STOREKEEPER', 'WAREHOUSE_WORKER')")
    public List<PickingTaskResponse> list(@RequestParam(required = false) PickingTask.Status status,
                                          @RequestParam(required = false) UUID requestId,
                                          @AuthenticationPrincipal Jwt jwt) {
        return pickingService.list(status, requestId, actor(jwt));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STOREKEEPER', 'WAREHOUSE_WORKER')")
    public PickingTaskResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return pickingService.get(id, actor(jwt));
    }

    @GetMapping("/{id}/progress")
    @PreAuthorize("hasAnyRole('STOREKEEPER', 'WAREHOUSE_WORKER')")
    public ProgressResponse progress(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return pickingService.progress(id, actor(jwt));
    }

    @GetMapping("/{id}/workers")
    @PreAuthorize("hasAnyRole('STOREKEEPER', 'WAREHOUSE_WORKER')")
    public List<WorkerResponse> workers(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return pickingService.workers(id, actor(jwt));
    }

    @PostMapping("/{id}/workers")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public PickingTaskResponse addWorkers(@PathVariable UUID id, @Valid @RequestBody AddWorkersDto dto,
                                          @AuthenticationPrincipal Jwt jwt) {
        return pickingService.addWorkers(id, dto, actor(jwt));
    }

    @PatchMapping("/{id}/workers/{workerId}")
    @PreAuthorize("hasAnyRole('STOREKEEPER', 'WAREHOUSE_WORKER')")
    public PickingTaskResponse updateWorker(@PathVariable UUID id, @PathVariable UUID workerId,
                                            @Valid @RequestBody UpdateWorkerDto dto, @AuthenticationPrincipal Jwt jwt) {
        return pickingService.updateWorker(id, workerId, dto, actor(jwt));
    }

    @DeleteMapping("/{id}/workers/{workerId}")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public PickingTaskResponse removeWorker(@PathVariable UUID id, @PathVariable UUID workerId,
                                            @RequestParam(required = false) UUID replaceWith,
                                            @RequestParam String reason, @AuthenticationPrincipal Jwt jwt) {
        return pickingService.removeWorker(id, workerId, replaceWith, reason, actor(jwt));
    }

    @PostMapping("/{id}/assign-items")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public PickingTaskResponse assignItems(@PathVariable UUID id, @Valid @RequestBody AssignItemsDto dto,
                                           @AuthenticationPrincipal Jwt jwt) {
        return pickingService.assignItems(id, dto, actor(jwt));
    }

    @PostMapping("/{id}/items/{itemId}/pick")
    @PreAuthorize("hasRole('WAREHOUSE_WORKER')")
    public PickingTaskResponse pick(@PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody PickDto dto,
                                    @AuthenticationPrincipal Jwt jwt) {
        return pickingService.pick(id, itemId, dto, actor(jwt));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('STOREKEEPER')")
    public PickingTaskResponse complete(@PathVariable UUID id, @Valid @RequestBody(required = false) CompleteTaskDto dto,
                                        @AuthenticationPrincipal Jwt jwt) {
        return pickingService.complete(id, dto, actor(jwt));
    }

    private static Actor actor(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean storekeeper = roles != null && roles.contains("STOREKEEPER");
        boolean admin = roles != null && roles.contains("ADMIN");
        return new Actor(UUID.fromString(jwt.getSubject()), storekeeper, admin);
    }
}
