package com.tripbudget.tripbudget_core.plantemplate.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(schema = "trip_core", name = "plan_template_saves", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plan_template_saves_user_template", columnNames = {"user_id", "template_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanTemplateSaveEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "template_id", nullable = false, updatable = false)
    private Long templateId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PlanTemplateSaveEntity create(Long userId, Long templateId) {
        PlanTemplateSaveEntity saved = new PlanTemplateSaveEntity();
        saved.userId = userId;
        saved.templateId = templateId;
        return saved;
    }

    @PrePersist
    private void recordCreationTime() {
        createdAt = Instant.now();
    }
}
