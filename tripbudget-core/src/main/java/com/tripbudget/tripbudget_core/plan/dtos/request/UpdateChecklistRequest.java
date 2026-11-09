package com.tripbudget.tripbudget_core.plan.dtos.request;

import jakarta.validation.constraints.Size;

import com.tripbudget.tripbudget_core.plan.enums.ChecklistCategory;

public record UpdateChecklistRequest(
        @Size(max = 255)
        String title,
        ChecklistCategory category,
        Boolean isCompleted,
        String assigneeId
) {}

