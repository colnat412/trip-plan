package com.tripbudget.tripbudget_core.expense.dtos.request;

import com.tripbudget.tripbudget_core.expense.enums.ExpenseCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;
import java.util.Map;

public record SetBudgetRequest(
        @NotNull(message = "Total budget is required")
        @Min(value = 0, message = "Total budget cannot be negative")
        @Digits(integer = 13, fraction = 2)
        BigDecimal totalBudget,

        String currency,

        Map<@NotNull ExpenseCategory, @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal> categoryLimits
) {}

