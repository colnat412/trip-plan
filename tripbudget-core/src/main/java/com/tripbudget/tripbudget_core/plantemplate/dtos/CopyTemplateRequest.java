package com.tripbudget.tripbudget_core.plantemplate.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CopyTemplateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull LocalDate startDate,
        boolean includeChecklist,
        @Min(1) int expectedContentVersion
) {}
