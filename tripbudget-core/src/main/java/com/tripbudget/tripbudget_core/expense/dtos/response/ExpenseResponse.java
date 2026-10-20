package com.tripbudget.tripbudget_core.expense.dtos.response;

import com.tripbudget.tripbudget_core.expense.enums.ExpenseCategory;
import com.tripbudget.tripbudget_core.expense.enums.ExpenseStatus;
import com.tripbudget.tripbudget_core.expense.enums.SplitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ExpenseResponse(
        String id,
        String createdBy,
        String tripId,
        String payerId,
        String payerName,
        String payerEmail,
        String payerAvatarUrl,
        String title,
        ExpenseCategory category,
        BigDecimal amount,
        String currency,
        LocalDate expenseDate,
        SplitType splitType,
        ExpenseStatus status,
        String note,
        String receiptUrl,
        java.util.UUID receiptUploadId,
        String activityId,
        Instant createdAt,
        Instant updatedAt,
        List<ExpenseSplitResponse> splits
) {}

