package com.tripbudget.tripbudget_core.admin.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;

import jakarta.persistence.*;

import lombok.*;

@Getter
@Entity
@Table(schema = "trip_core", name = "admin_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditEntity extends BaseEntity {
    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Column(name = "actor_label", nullable = false, length = 320, updatable = false)
    private String actorLabel;

    @Column(nullable = false, length = 40, updatable = false)
    private String action;

    @Column(name = "target_label", nullable = false, length = 320, updatable = false)
    private String targetLabel;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(nullable = false, length = 20)
    private String outcome;

    public static AdminAuditEntity create(
            Long actorId,
            String actorLabel,
            String action,
            String targetLabel,
            String reason,
            String outcome) {
        var entry = new AdminAuditEntity();
        entry.actorId = actorId;
        entry.actorLabel = actorLabel;
        entry.action = action;
        entry.targetLabel = targetLabel;
        entry.reason = reason;
        entry.outcome = outcome;
        return entry;
    }

    public void complete(boolean success) {
        outcome = success ? "SUCCESS" : "FAILED";
    }
}
