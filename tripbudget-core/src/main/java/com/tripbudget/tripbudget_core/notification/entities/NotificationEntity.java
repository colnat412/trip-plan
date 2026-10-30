package com.tripbudget.tripbudget_core.notification.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import com.tripbudget.tripbudget_core.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "trip_core", name = "notifications",
       uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "recipient_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEntity extends BaseEntity {
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "recipient_id", nullable = false) private Long recipientId;
    @Column(name = "actor_id", nullable = false) private Long actorId;
    @Column(name = "trip_id", nullable = false) private Long tripId;
    @Column(name = "target_id") private Long targetId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50) private NotificationType type;
    @Column(name = "read_at") private Instant readAt;

    public static NotificationEntity create(UUID eventId, Long recipientId, Long actorId,
            Long tripId, Long targetId, NotificationType type) {
        var notification = new NotificationEntity();
        notification.eventId = eventId;
        notification.recipientId = recipientId;
        notification.actorId = actorId;
        notification.tripId = tripId;
        notification.targetId = targetId;
        notification.type = type;
        return notification;
    }
}
