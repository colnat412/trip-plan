package com.tripbudget.tripbudget_core.plan.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderDayActivitiesRequest(
        @NotEmpty(message = "Activity IDs are required")
        List<@NotBlank String> activityIds
) {}
