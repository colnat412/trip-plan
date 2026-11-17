package com.tripbudget.tripbudget_core.expense.dtos.response;

import java.math.BigDecimal;
import java.util.List;

public record TripBudgetSummaryResponse(
        String tripId,
        BigDecimal totalBudget,
        BigDecimal actualSpent,
        BigDecimal remainingBudget,
        double percentageUsed,
        String currency,
        List<CategoryBreakdownResponse> categoryBreakdown,
        List<DailySpendingResponse> dailyTotals
) {}

