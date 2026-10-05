package ru.warehouse.common.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/** Стабильный формат страницы, не зависящий от внутренностей Spring Data. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages());
    }
}