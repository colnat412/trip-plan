package com.tripbudget.tripbudget_core.settlement.dtos.request;

import com.tripbudget.tripbudget_core.settlement.enums.SettlementAction;
import jakarta.validation.constraints.NotNull;

public record RespondSettlementRequest(@NotNull SettlementAction action) {}
