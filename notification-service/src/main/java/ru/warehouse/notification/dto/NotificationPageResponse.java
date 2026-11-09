package ru.warehouse.notification.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Если в common-lib PageResponse подходит по форме, замените этот класс на него. */
public record NotificationPageResponse(
        List<NotificationResponse> content,   // было: items
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static NotificationPageResponse from(Page<NotificationResponse> p) {
        return new NotificationPageResponse(p.getContent(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages());
    }
}
