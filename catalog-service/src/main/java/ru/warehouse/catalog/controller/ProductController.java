package ru.warehouse.catalog.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.catalog.dto.CatalogDtos.*;
import ru.warehouse.catalog.service.ProductService;
import ru.warehouse.catalog.service.ProductUsageGuard;
import ru.warehouse.common.dto.PageResponse;
import java.util.List;
import java.util.UUID;

/**
 * Чтение: любая аутентифицированная роль (проверяет JWT-фильтр).
 * Изменение номенклатуры: только ADMIN (разд. 4 ТЗ).
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final ProductUsageGuard usageGuard;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String q,                  // поиск по названию/артикулу
            @RequestParam(required = false) UUID productTypeId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {
        return productService.search(q, productTypeId, includeArchived, page, size, sortBy, direction);
    }

    // GET /api/v1/products/batch?ids=<uuid>,<uuid>,... (для других сервисов); литеральный путь приоритетнее /{id}
    @GetMapping("/batch")
    public List<ProductResponse> batch(@RequestParam List<UUID> ids) {
        return productService.findByIds(ids);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return productService.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse archive(@PathVariable UUID id) {
        ProductResponse current = productService.get(id);
        if (!current.active()) {
            return current; // уже в архиве: операция идемпотентна
        }
        usageGuard.assertArchivable(id); // M4: остатки и открытые документы, до транзакции архивации
        return productService.archive(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse restore(@PathVariable UUID id) {
        return productService.restore(id);
    }
}