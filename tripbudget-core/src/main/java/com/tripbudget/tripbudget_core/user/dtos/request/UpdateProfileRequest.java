package com.tripbudget.tripbudget_core.user.dtos.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(@NotBlank @Size(max = 100) String name) {
    public UpdateProfileRequest {
        if (name != null) name = name.strip();
    }

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unexpected field: " + name);
    }
}
