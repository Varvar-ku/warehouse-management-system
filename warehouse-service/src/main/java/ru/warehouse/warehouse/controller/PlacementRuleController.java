// PlacementRuleController.java
package ru.warehouse.warehouse.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.warehouse.dto.WarehouseDtos.*;
import ru.warehouse.warehouse.service.PlacementRuleService;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/placement-rules")
@RequiredArgsConstructor
public class PlacementRuleController {
    private final PlacementRuleService ruleService;

    @GetMapping
    public PageResponse<RuleResponse> search(@RequestParam(required = false) UUID warehouseId,
                                             @RequestParam(required = false) Boolean active,
                                             @RequestParam(required = false) Boolean fallback,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return ruleService.search(warehouseId, active, fallback, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RuleResponse create(@Valid @RequestBody RuleRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ruleService.create(request, actor(jwt));
    }

    @GetMapping("/{id}")
    public RuleResponse get(@PathVariable UUID id) {
        return ruleService.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RuleResponse update(@PathVariable UUID id, @Valid @RequestBody RuleUpdateRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return ruleService.update(id, request, actor(jwt));
    }

    /** История версий правила (от новой к старой). */
    @GetMapping("/{id}/versions")
    public List<RuleVersionResponse> versions(@PathVariable UUID id) {
        return ruleService.versions(id);
    }

    private static UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}