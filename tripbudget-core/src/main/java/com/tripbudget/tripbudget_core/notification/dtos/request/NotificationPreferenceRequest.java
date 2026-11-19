package com.tripbudget.tripbudget_core.notification.dtos.request;

import jakarta.validation.constraints.*;
public record NotificationPreferenceRequest(@NotNull Boolean pushEnabled,
    @NotNull @Pattern(regexp = "vi|en") String locale) {}
