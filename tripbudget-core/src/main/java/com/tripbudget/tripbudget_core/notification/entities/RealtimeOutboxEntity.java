package com.tripbudget.tripbudget_core.notification.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "trip_core", name = "realtime_outbox",
       uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "recipient_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RealtimeOutboxEntity extends BaseEntity {
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "recipient_id", nullable = false) private Long recipientId;
    @Column(name = "notification_id") private Long notificationId;
    @Column(name = "event_type", nullable = false, length = 50) private String eventType;
    @Column(nullable = false, length = 20) private String status = "PENDING";
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt = Instant.now();
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "claim_token") private UUID claimToken;
    @Column(name = "dispatched_at") private Instant dispatchedAt;

    public static RealtimeOutboxEntity create(UUID eventId, Long recipientId,
            Long notificationId, String eventType) {
        var outbox = new RealtimeOutboxEntity();
        outbox.eventId = eventId;
        outbox.recipientId = recipientId;
        outbox.notificationId = notificationId;
        outbox.eventType = eventType;
        return outbox;
    }
}
