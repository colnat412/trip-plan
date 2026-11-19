package com.tripbudget.tripbudget_core.notification.dtos.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
public record ReadAllRequest(@NotNull Instant through) {}
