package com.tripbudget.tripbudget_core.auth.entities;

import com.tripbudget.tripbudget_core.auth.session.SessionClientInfo;
import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(schema = "trip_core", name = "login_sessions", uniqueConstraints = @UniqueConstraint(name = "uq_login_sessions_sid", columnNames = "session_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginSessionEntity extends BaseEntity {
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(name = "signed_in_at", nullable = false, updatable = false)
    private Instant signedInAt;
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
    @Column(name = "last_refreshed_at")
    private Instant lastRefreshedAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "client_type", nullable = false, length = 20, updatable = false)
    private String clientType;
    @Column(nullable = false, length = 80, updatable = false)
    private String browser;
    @Column(name = "operating_system", nullable = false, length = 80, updatable = false)
    private String operatingSystem;
    @Column(name = "device_type", nullable = false, length = 20, updatable = false)
    private String deviceType;
    @Column(name = "login_method", nullable = false, length = 20, updatable = false)
    private String loginMethod;
    @Column(name = "user_agent", nullable = false, length = 512, updatable = false)
    private String userAgent;

    public static LoginSessionEntity create(Long userId, UUID id, Instant created, Instant expires, SessionClientInfo info) {
        var entity = new LoginSessionEntity();
        entity.userId = userId; entity.sessionId = id; entity.signedInAt = created; entity.expiresAt = expires;
        entity.status = "ACTIVE"; entity.clientType = info.clientType(); entity.browser = info.browser();
        entity.operatingSystem = info.operatingSystem(); entity.deviceType = info.deviceType();
        entity.loginMethod = info.loginMethod(); entity.userAgent = info.userAgent();
        return entity;
    }

    public void refreshed(Instant at) {
        if ("ACTIVE".equals(status) && (lastRefreshedAt == null || at.isAfter(lastRefreshedAt))) lastRefreshedAt = at;
    }

    public void end(String outcome, Instant at) {
        if (!java.util.Set.of("LOGGED_OUT", "REVOKED", "FAILED").contains(outcome)) throw new IllegalArgumentException("Invalid session outcome");
        if ("ACTIVE".equals(status)) { status = outcome; endedAt = at; }
    }
}
