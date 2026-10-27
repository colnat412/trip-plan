package com.tripbudget.tripbudget_core.plantemplate.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

public record PlanTemplateCardResponse(
        String id,
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
        @JsonInclude(JsonInclude.Include.NON_NULL) String sourceTripId
) {}
