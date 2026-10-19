package com.tripbudget.tripbudget_core.expense.dtos.request;

import com.tripbudget.tripbudget_core.expense.enums.ExpenseCategory;
import com.tripbudget.tripbudget_core.expense.enums.SplitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateExpenseRequest(
        @NotBlank(message = "Title is required")
        String title,

        @NotNull(message = "Category is required")
        ExpenseCategory category,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than 0")
        BigDecimal amount,

        String currency,

        @NotNull(message = "Expense date is required")
        LocalDate expenseDate,

        SplitType splitType,

        String note,

        @jakarta.validation.constraints.Size(max = 500)
        String receiptUrl,

        String payerId,

        String activityId,

        List<SplitItemRequest> splits,

        java.util.UUID receiptUploadId
) {}

