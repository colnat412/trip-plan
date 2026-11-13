package com.tripbudget.tripbudget_core.plan.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(
        schema = "trip_core",
        name = "plan_days",
        indexes = {
                @Index(name = "idx_plan_days_trip", columnList = "trip_id, day_number")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_plan_days_trip_day", columnNames = {"trip_id", "day_number"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanDayEntity extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(name = "plan_date")
    private LocalDate planDate;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startTime ASC, orderIndex ASC, createdAt ASC, id ASC")
    private List<PlanActivityEntity> activities = new ArrayList<>();

    public static PlanDayEntity create(
            Long tripId,
            Integer dayNumber,
            LocalDate planDate,
            String title,
            String note
    ) {
        PlanDayEntity day = new PlanDayEntity();
        day.tripId = tripId;
        day.dayNumber = dayNumber;
        day.planDate = planDate;
        day.title = title != null && !title.isBlank() ? title.trim() : null;
        day.note = note != null && !note.isBlank() ? note.trim() : null;
        return day;
    }

    public void updateDetails(LocalDate planDate, String title, String note) {
        if (planDate != null) {
            this.planDate = planDate;
        }
        if (title != null) {
            this.title = title.trim();
        }
        if (note != null) {
            this.note = note.trim();
        }
    }
}

