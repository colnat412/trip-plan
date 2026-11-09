package com.tripbudget.tripbudget_core.plan.dtos.request;

import jakarta.validation.constraints.Size;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreatePlanDayRequest(
        @NotNull(message = "Day number is required")
        @Positive(message = "Day number must be greater than 0")
        Integer dayNumber,

        LocalDate planDate,

        @Size(max = 255)
        String title,

        String note
) {}

