package com.tripbudget.tripbudget_core.auth.dtos.request;

import jakarta.validation.constraints.*;

public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {}
