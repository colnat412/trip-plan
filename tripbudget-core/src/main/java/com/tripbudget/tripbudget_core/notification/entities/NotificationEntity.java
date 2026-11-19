package com.tripbudget.tripbudget_core.notification.entities;

import com.tripbudget.tripbudget_core.notification.enums.NotificationType;
import jakarta.persistence.*;
import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

// Reading and deleting in different tabs must not overwrite each other's fields.
@org.hibernate.annotations.DynamicUpdate
@Entity
@Table(schema = "trip_core", name = "notifications")
@Getter
@NoArgsConstructor
public class NotificationEntity extends BaseEntity {
    @Column(nullable = false) private Long recipientId;
    @Column(nullable = false) private Long tripId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50)
    private NotificationType type;
    @Column(nullable = false, length = 200) private String tripName;
    @Column(nullable = false, length = 255) private String subject;
    private Long targetId;
    private Instant readAt;
    private Instant expiresAt;
    @Column(nullable = false, unique = true, length = 255) private String dedupeKey;

    public static NotificationEntity create(Long recipientId, Long tripId, String tripName,
            NotificationType type, String subject, Long targetId, String key, Instant expiresAt) {
        NotificationEntity notification = new NotificationEntity();
        notification.recipientId = recipientId;
        notification.tripId = tripId;
        notification.tripName = tripName;
        notification.type = type;
        notification.subject = subject == null ? "" : subject.substring(0, Math.min(subject.length(), 255));
        notification.targetId = targetId;
        notification.dedupeKey = key;
        notification.expiresAt = expiresAt;
        return notification;
    }
    public void resolveInvitation(boolean accept, Instant now) {
        if (type != NotificationType.MEMBER_INVITED) throw new IllegalStateException("Invitation already resolved");
        type = accept ? NotificationType.MEMBER_INVITATION_ACCEPTED : NotificationType.MEMBER_INVITATION_DECLINED;
        markRead(now);
    }
    public void cancelInvitation() {
        if (type == NotificationType.MEMBER_INVITED) type = NotificationType.MEMBER_INVITATION_CANCELLED;
    }
    public void markRead(Instant now) { if (readAt == null) readAt = now; }
}
