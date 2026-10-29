package com.tripbudget.tripbudget_core.plantemplate.repositories;

import java.math.BigDecimal;

public record PlanTemplateSummary(
        Long id,
        Long sourceTripId,
        String title,
        String destination,
        String description,
        String tags,
        int durationDays,
        int activityCount,
        BigDecimal estimatedCost,
        String currency,
        String status,
        int contentVersion,
        String authorName,
        long usageCount,
        String coverUrl
) {}
