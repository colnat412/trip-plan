package com.tripbudget.tripbudget_core.notification.dtos.request;

import jakarta.validation.constraints.*;
import java.util.UUID;
public record PushDeviceRequest(@NotNull UUID installationId,
    @NotBlank @Pattern(regexp = "FCM_FID|FCM_TOKEN") String addressType,
    @NotBlank @Size(max = 2048) String addressValue) {}
