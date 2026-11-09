package ru.warehouse.notification.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.warehouse.notification.dto.NotificationPageResponse;
import ru.warehouse.notification.dto.NotificationResponse;
import ru.warehouse.notification.dto.UnreadCountResponse;
import ru.warehouse.notification.entity.Notification;
import ru.warehouse.notification.repository.NotificationRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse list(UUID userId, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> result = unreadOnly
                ? repository.findByRecipientIdAndReadAtIsNull(userId, pageable)
                : repository.findByRecipientId(userId, pageable);
        return NotificationPageResponse.from(result.map(NotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(UUID userId) {
        return new UnreadCountResponse(repository.countByRecipientIdAndReadAtIsNull(userId));
    }

    /** Чужое или несуществующее уведомление: 404 (как и для чужих заявок). Повторная отметка безвредна. */
    @Transactional
    public NotificationResponse markRead(UUID userId, UUID id) {
        Notification n = repository.findByIdAndRecipientId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.markRead(Instant.now());
        return NotificationResponse.from(n);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId, Instant.now());
    }
}
