package com.tripbudget.tripbudget_core.plan.dtos.response;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.plan.entities.PlanActivityEntity;
import com.tripbudget.tripbudget_core.plan.enums.ActivityCategory;
import com.tripbudget.tripbudget_core.plan.enums.ActivityStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

public record PlanActivityResponse(
        String id,
        String createdBy,
        String dayId,
        String tripId,
        String title,
        LocalTime startTime,
        LocalTime endTime,
        String location,
        ActivityCategory category,
        BigDecimal estimatedCost,
        ActivityStatus status,
        Integer orderIndex,
        String note,
        // null in the public view.
        String expenseId,
        // null when unlinked or in the public view.
        BigDecimal actualSpent,
        Instant createdAt,
        Instant updatedAt
) {
    public static PlanActivityResponse from(PlanActivityEntity entity, HashidsService hashidsService, BigDecimal actualSpent) {
        return new PlanActivityResponse(
                hashidsService.encode(entity.getId()),
                entity.getCreatedBy() != null ? hashidsService.encode(entity.getCreatedBy()) : null,
                hashidsService.encode(entity.getDay().getId()),
                hashidsService.encode(entity.getTripId()),
                entity.getTitle(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getLocation(),
                entity.getCategory(),
                entity.getEstimatedCost() != null ? entity.getEstimatedCost() : BigDecimal.ZERO,
                entity.getStatus(),
                entity.getOrderIndex(),
                entity.getNote(),
                entity.getExpenseId() != null ? hashidsService.encode(entity.getExpenseId()) : null,
                actualSpent,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static PlanActivityResponse from(PlanActivityEntity entity, HashidsService hashidsService) {
        return from(entity, hashidsService, null);
    }

    public static PlanActivityResponse publicFrom(PlanActivityEntity entity, HashidsService hashidsService) {
        PlanActivityResponse full = from(entity, hashidsService, null);
        return new PlanActivityResponse(
                full.id(), null, full.dayId(), full.tripId(), full.title(),
                full.startTime(), full.endTime(), full.location(), full.category(),
                full.estimatedCost(), full.status(), full.orderIndex(), full.note(),
                null, null,
                full.createdAt(), full.updatedAt()
        );
    }
}

