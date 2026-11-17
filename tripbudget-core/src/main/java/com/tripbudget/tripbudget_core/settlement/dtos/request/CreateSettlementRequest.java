package com.tripbudget.tripbudget_core.settlement.dtos.request;

import com.tripbudget.tripbudget_core.settlement.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateSettlementRequest(
        String payerId,

        @NotBlank(message = "Payee ID is required")
        String payeeId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than 0")
        BigDecimal amount,

        String currency,

        LocalDateTime settledAt,

        PaymentMethod paymentMethod,

        String note,

        String receiptUrl
) {}

