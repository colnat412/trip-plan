package com.tripbudget.tripbudget_core.notification.entities;

import jakarta.persistence.*;
import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "trip_core", name = "notification_deliveries",
    uniqueConstraints = @UniqueConstraint(columnNames = {"notification_id", "device_id"}))
@Getter
@NoArgsConstructor
public class NotificationDeliveryEntity extends BaseEntity {
    @Column(nullable = false) private Long notificationId;
    @Column(nullable = false) private Long deviceId;
    @Column(nullable = false) private long deviceGeneration;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private Instant nextAttemptAt;
    private Instant lockedUntil;
    private UUID claimToken;
    @Column(length = 255) private String providerMessageId;
    @Column(length = 100) private String lastError;
    public static NotificationDeliveryEntity create(Long notificationId, PushDeviceEntity device, Instant now) {
        NotificationDeliveryEntity delivery = new NotificationDeliveryEntity();
        delivery.notificationId = notificationId;
        delivery.deviceId = device.getId();
        delivery.deviceGeneration = device.getGeneration();
        delivery.status = "PENDING";
        delivery.nextAttemptAt = now;
        return delivery;
    }
    public void claim(Instant now) {
        status = "SENDING";
        attempts++;
        claimToken = UUID.randomUUID();
        lockedUntil = now.plusSeconds(120);
    }
    public boolean owns(UUID token) { return "SENDING".equals(status) && token.equals(claimToken); }
    public void complete(String status, String providerId, String error, Instant next) {
        this.status = status;
        this.providerMessageId = providerId;
        this.lastError = error;
        this.nextAttemptAt = next;
        this.claimToken = null;
        this.lockedUntil = null;
    }
}
