package ru.warehouse.catalog.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.catalog.dto.CatalogDtos.*;
import ru.warehouse.catalog.service.ReferenceService;
import java.util.List;
import java.util.UUID;

/** Справочники: чтение доступно любой роли, изменение только ADMIN. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReferenceController {
    private final ReferenceService referenceService;

    @GetMapping("/product-types")
    public List<ProductTypeResponse> listTypes(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return referenceService.listTypes(includeInactive);
    }

    @PostMapping("/product-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductTypeResponse createType(@Valid @RequestBody ProductTypeRequest request) {
        return referenceService.createType(request);
    }

    @PatchMapping("/product-types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductTypeResponse updateType(@PathVariable UUID id, @Valid @RequestBody ProductTypeUpdateRequest request) {
        return referenceService.updateType(id, request);
    }

    @GetMapping("/units")
    public List<UnitResponse> listUnits() {
        return referenceService.listUnits();
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UnitResponse createUnit(@Valid @RequestBody UnitRequest request) {
        return referenceService.createUnit(request);
    }

    @GetMapping("/attribute-definitions")
    public List<AttributeDefinitionResponse> listDefinitions() {
        return referenceService.listDefinitions();
    }

    @PostMapping("/attribute-definitions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AttributeDefinitionResponse createDefinition(@Valid @RequestBody AttributeDefinitionRequest request) {
        return referenceService.createDefinition(request);
    }
}