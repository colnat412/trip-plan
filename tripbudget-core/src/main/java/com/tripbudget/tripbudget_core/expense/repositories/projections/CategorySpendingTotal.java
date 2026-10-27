package com.tripbudget.tripbudget_core.expense.repositories.projections;

import com.tripbudget.tripbudget_core.expense.enums.ExpenseCategory;
import java.math.BigDecimal;

public interface CategorySpendingTotal {
    ExpenseCategory getCategory();
    BigDecimal getTotalSpent();
}
