package com.tripbudget.tripbudget_core.auth.dtos.request;

import jakarta.validation.constraints.*;

public record VerifyResetCodeRequest(@NotBlank @Email @Size(max = 254) String email,
                                     @NotBlank @Pattern(regexp = "\\d{6}") String otp) {
    @Override public String toString() { return "VerifyResetCodeRequest[REDACTED]"; }
}
