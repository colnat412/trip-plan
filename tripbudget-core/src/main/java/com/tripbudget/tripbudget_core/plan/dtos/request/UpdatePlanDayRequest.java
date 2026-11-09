package com.tripbudget.tripbudget_core.plan.dtos.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdatePlanDayRequest(
        LocalDate planDate,
        @Size(max = 255)
        String title,
        String note
) {}

