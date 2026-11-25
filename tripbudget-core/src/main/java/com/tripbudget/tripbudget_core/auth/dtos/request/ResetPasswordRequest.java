package com.tripbudget.tripbudget_core.auth.dtos.request;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String resetToken,
                                   @NotBlank @Size(min = 8, max = 72) String newPassword) {
    @Override public String toString() { return "ResetPasswordRequest[REDACTED]"; }
}
