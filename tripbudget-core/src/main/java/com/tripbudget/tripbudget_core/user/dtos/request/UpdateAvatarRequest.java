package com.tripbudget.tripbudget_core.user.dtos.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateAvatarRequest(
        @NotNull @Pattern(regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") String uploadId
) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unexpected field: " + name);
    }
}
