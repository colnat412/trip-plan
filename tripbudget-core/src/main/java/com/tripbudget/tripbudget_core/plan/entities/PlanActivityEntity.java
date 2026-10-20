package com.tripbudget.tripbudget_core.plan.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import com.tripbudget.tripbudget_core.plan.enums.ActivityCategory;
import com.tripbudget.tripbudget_core.plan.enums.ActivityStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Entity
@Table(
        schema = "trip_core",
        name = "plan_activities",
        indexes = {
                @Index(name = "idx_plan_activities_day", columnList = "day_id, order_index"),
                @Index(name = "idx_plan_activities_trip", columnList = "trip_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanActivityEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private PlanDayEntity day;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ActivityCategory category;

    @Column(name = "estimated_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityStatus status;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "expense_id")
    private Long expenseId;

    public static PlanActivityEntity create(
            Long createdBy,
            PlanDayEntity day,
            Long tripId,
            String title,
            LocalTime startTime,
            LocalTime endTime,
            String location,
            ActivityCategory category,
            BigDecimal estimatedCost,
            Integer orderIndex,
            String note
    ) {
        PlanActivityEntity act = new PlanActivityEntity();
        act.createdBy = java.util.Objects.requireNonNull(createdBy, "createdBy is required");
        act.day = day;
        act.tripId = tripId;
        act.title = title.trim();
        act.startTime = startTime;
        act.endTime = endTime;
        act.location = location != null && !location.isBlank() ? location.trim() : null;
        act.category = category != null ? category : ActivityCategory.OTHER;
        act.estimatedCost = estimatedCost != null ? estimatedCost : BigDecimal.ZERO;
        act.status = ActivityStatus.PLANNED;
        act.orderIndex = orderIndex != null ? orderIndex : 0;
        act.note = note != null && !note.isBlank() ? note.trim() : null;
        return act;
    }

    public void updateDetails(
            String title,
            LocalTime startTime,
            LocalTime endTime,
            String location,
            ActivityCategory category,
            BigDecimal estimatedCost,
            ActivityStatus status,
            Integer orderIndex,
            String note,
            Long expenseId
    ) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location != null && !location.isBlank() ? location.trim() : null;
        if (category != null) {
            this.category = category;
        }
        if (estimatedCost != null) {
            this.estimatedCost = estimatedCost;
        }
        if (status != null) {
            this.status = status;
        }
        if (orderIndex != null) {
            this.orderIndex = orderIndex;
        }
        this.note = note != null && !note.isBlank() ? note.trim() : null;
        if (expenseId != null) {
            this.expenseId = expenseId;
        }
    }

    public void updateStatus(ActivityStatus newStatus) {
        if (newStatus != null) {
            this.status = newStatus;
        }
    }

    public void setExpenseId(Long expenseId) {
        this.expenseId = expenseId;
    }

    public void assignDay(PlanDayEntity newDay) {
        this.day = newDay;
    }
}

