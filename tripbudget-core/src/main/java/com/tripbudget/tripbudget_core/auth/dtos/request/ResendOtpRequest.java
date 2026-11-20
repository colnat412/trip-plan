package com.tripbudget.tripbudget_core.auth.dtos.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(
        @NotBlank @Email String email
) {}
