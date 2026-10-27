package com.tripbudget.tripbudget_core.plantemplate.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(schema = "trip_core", name = "plan_template_copies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanTemplateCopyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long templateId;

    @Column(nullable = false)
    private int templateVersion;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private Long newTripId;

    @Column(nullable = false)
    private UUID idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static PlanTemplateCopyEntity create(
            Long templateId, int templateVersion, Long userId, Long newTripId,
            UUID idempotencyKey, String requestHash
    ) {
        PlanTemplateCopyEntity receipt = new PlanTemplateCopyEntity();
        receipt.templateId = templateId;
        receipt.templateVersion = templateVersion;
        receipt.userId = userId;
        receipt.newTripId = newTripId;
        receipt.idempotencyKey = idempotencyKey;
        receipt.requestHash = requestHash;
        return receipt;
    }

    @PrePersist
    private void recordCreationTime() {
        createdAt = Instant.now();
    }
}
