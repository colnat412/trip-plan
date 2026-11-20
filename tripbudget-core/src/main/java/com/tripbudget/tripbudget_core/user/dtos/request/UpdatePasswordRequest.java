package com.tripbudget.tripbudget_core.user.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePasswordRequest(
        String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {
    @Override
    public String toString() {
        return "UpdatePasswordRequest[credentials=REDACTED]";
    }
}
