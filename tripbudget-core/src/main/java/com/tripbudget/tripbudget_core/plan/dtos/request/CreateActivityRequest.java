package com.tripbudget.tripbudget_core.plan.dtos.request;

import com.tripbudget.tripbudget_core.plan.enums.ActivityCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.LocalTime;

public record CreateActivityRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255)
        String title,

        LocalTime startTime,

        LocalTime endTime,

        @Size(max = 255)
        String location,

        ActivityCategory category,

        @PositiveOrZero
        @Digits(integer = 13, fraction = 2)
        BigDecimal estimatedCost,

        @PositiveOrZero
        Integer orderIndex,

        String note
) {
    @AssertTrue(message = "End time must be at or after start time on the same day")
    @JsonIgnore
    public boolean isTimeRangeValid() {
        return startTime == null || endTime == null || !endTime.isBefore(startTime);
    }
}

