package com.tripbudget.tripbudget_core.plantemplate.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PublishTemplateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 255) String destination,
        @Size(max = 5000) String description,
        @NotNull @Size(max = 5)
        List<@Pattern(regexp = "RELAXED|ADVENTURE|FOOD|CULTURE|FAMILY|NATURE") String> tags,
        boolean includeNotes,
        @NotNull @Size(max = 200) List<@NotBlank String> checklistIds,
        @Size(max = 64) String sourceFingerprint,
        Integer expectedContentVersion
) {}
