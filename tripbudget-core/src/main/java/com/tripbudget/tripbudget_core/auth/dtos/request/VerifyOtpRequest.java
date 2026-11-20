package com.tripbudget.tripbudget_core.auth.dtos.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank @Email String email, @NotBlank @Pattern(regexp = "[0-9]{6}") String otp
) {
    @Override
    public String toString() {
        return "VerifyOtpRequest[otp=REDACTED]";
    }
}
