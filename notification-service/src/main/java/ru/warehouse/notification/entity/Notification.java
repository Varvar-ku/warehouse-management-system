package ru.warehouse.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 40)
    private NotificationType type;

    @Column(name = "title", nullable = false, updatable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, updatable = false, length = 1000)
    private String message;

    @Column(name = "request_id", updatable = false)
    private UUID requestId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "trace_id", updatable = false, length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public static Notification create(UUID recipientId, NotificationType type, String title, String message,
                                      UUID requestId, UUID eventId, String traceId) {
        Notification n = new Notification();
        n.recipientId = recipientId;
        n.type = type;
        n.title = title;
        n.message = message;
        n.requestId = requestId;
        n.eventId = eventId;
        n.traceId = traceId;
        n.createdAt = Instant.now();
        return n;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public UUID getId() { return id; }
    public UUID getRecipientId() { return recipientId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public UUID getRequestId() { return requestId; }
    public UUID getEventId() { return eventId; }
    public String getTraceId() { return traceId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
