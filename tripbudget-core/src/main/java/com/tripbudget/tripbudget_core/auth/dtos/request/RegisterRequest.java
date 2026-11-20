package com.tripbudget.tripbudget_core.auth.dtos.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email, @NotBlank @Size(min = 8) String password, @NotBlank @Size(max = 255) String name
) {
    @Override
    public String toString() {
        return "RegisterRequest[credentials=REDACTED]";
    }
}
