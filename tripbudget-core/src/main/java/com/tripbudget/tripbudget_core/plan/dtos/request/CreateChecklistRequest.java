package com.tripbudget.tripbudget_core.plan.dtos.request;

import jakarta.validation.constraints.Size;

import com.tripbudget.tripbudget_core.plan.enums.ChecklistCategory;
import jakarta.validation.constraints.NotBlank;

public record CreateChecklistRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255)
        String title,

        ChecklistCategory category,

        String assigneeId
) {}

