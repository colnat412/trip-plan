package com.tripbudget.tripbudget_core.expense.repositories.projections;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailySpendingTotal {
    LocalDate getDate();
    String getCurrency();
    BigDecimal getAmount();
}
