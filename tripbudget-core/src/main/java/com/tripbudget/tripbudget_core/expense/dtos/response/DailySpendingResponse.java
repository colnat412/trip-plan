package com.tripbudget.tripbudget_core.expense.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySpendingResponse(LocalDate expenseDate, String currency, BigDecimal amount) {}
