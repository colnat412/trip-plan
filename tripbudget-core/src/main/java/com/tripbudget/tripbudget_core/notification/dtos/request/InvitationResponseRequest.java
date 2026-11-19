package com.tripbudget.tripbudget_core.notification.dtos.request;

import jakarta.validation.constraints.NotNull;
public record InvitationResponseRequest(@NotNull Boolean accept) {}
