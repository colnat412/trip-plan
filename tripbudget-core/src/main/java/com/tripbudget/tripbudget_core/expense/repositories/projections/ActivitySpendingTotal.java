package com.tripbudget.tripbudget_core.expense.repositories.projections;

import java.math.BigDecimal;

public interface ActivitySpendingTotal {
    Long getActivityId();
    BigDecimal getTotalSpent();
}
