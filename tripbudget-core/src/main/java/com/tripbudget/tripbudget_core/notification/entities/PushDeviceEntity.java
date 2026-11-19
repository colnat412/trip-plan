package com.tripbudget.tripbudget_core.notification.entities;

import jakarta.persistence.*;
import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "trip_core", name = "push_devices")
@Getter
@NoArgsConstructor
public class PushDeviceEntity extends BaseEntity {
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 100) private String sessionId;
    @Column(nullable = false) private UUID installationId;
    @Column(nullable = false, length = 20) private String addressType;
    @Column(nullable = false, length = 2048, unique = true) private String addressValue;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false) private Instant lastSeenAt;
    @Column(nullable = false) private long generation;
    public static PushDeviceEntity create(UUID installationId) {
        PushDeviceEntity device = new PushDeviceEntity();
        device.installationId = installationId;
        return device;
    }
    public void register(Long userId, String sessionId, String type, String address, Instant now) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.addressType = type;
        this.addressValue = address;
        this.enabled = true;
        this.lastSeenAt = now;
        this.generation++;
    }
    public void seen(Instant now) { this.lastSeenAt = now; }
    public void revoke() { this.enabled = false; this.generation++; }
}
