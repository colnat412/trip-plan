package com.tripbudget.tripbudget_core.user.dtos.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record DeleteAccountRequest(@NotNull @AssertTrue Boolean confirmed) {}
