package com.tripbudget.tripbudget_core.plantemplate.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import com.tripbudget.tripbudget_core.plantemplate.dtos.PublishTemplateRequest;
import com.tripbudget.tripbudget_core.plantemplate.dtos.TemplateSnapshot;
import com.tripbudget.tripbudget_core.plantemplate.enums.PlanTemplateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(schema = "trip_core", name = "plan_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanTemplateEntity extends BaseEntity {
    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, unique = true)
    private Long sourceTripId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 255)
    private String destination;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 150)
    private String tags;

    @Column(nullable = false)
    private int durationDays;

    @Column(nullable = false)
    private int activityCount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal estimatedCost;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanTemplateStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private TemplateSnapshot snapshot;

    @Column(nullable = false)
    private int contentVersion;

    @Version
    @Column(nullable = false)
    private Long rowVersion;

    @Column(nullable = false)
    private Instant publishedAt;

    @Column(name = "cover_upload_id")
    private UUID coverUploadId;

    @Column(name = "cover_url", columnDefinition = "text")
    private String coverUrl;

    public void updateCover(UUID uploadId, String url) {
        this.coverUploadId = uploadId;
        this.coverUrl = url;
    }

    public static PlanTemplateEntity create(Long authorId, Long sourceTripId) {
        PlanTemplateEntity template = new PlanTemplateEntity();
        template.authorId = authorId;
        template.sourceTripId = sourceTripId;
        return template;
    }

    public void publish(PublishTemplateRequest request, TemplateSnapshot snapshot, String currency) {
        this.title = request.title().trim();
        this.destination = request.destination().trim();
        this.description = request.description() == null ? null : request.description().trim();
        this.tags = String.join(",", request.tags().stream().distinct().sorted().toList());
        this.snapshot = snapshot;
        this.currency = currency;
        this.durationDays = snapshot.days().size();
        this.activityCount = snapshot.days().stream().mapToInt(day -> day.activities().size()).sum();
        this.estimatedCost = snapshot.days().stream()
                .flatMap(day -> day.activities().stream())
                .map(TemplateSnapshot.Activity::estimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.status = PlanTemplateStatus.PUBLISHED;
        this.contentVersion++;
        this.publishedAt = Instant.now();
    }

    public void unpublish() {
        status = PlanTemplateStatus.UNPUBLISHED;
    }
}
