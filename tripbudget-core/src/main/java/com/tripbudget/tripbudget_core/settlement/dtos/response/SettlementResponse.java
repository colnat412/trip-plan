package com.tripbudget.tripbudget_core.settlement.dtos.response;

import com.tripbudget.tripbudget_core.settlement.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SettlementResponse(
        String id,
        String createdBy,
        String tripId,
        String payerId,
        String payerName,
        String payerEmail,
        String payerAvatarUrl,
        String payeeId,
        String payeeName,
        String payeeEmail,
        String payeeAvatarUrl,
        BigDecimal amount,
        String currency,
        LocalDate settledAt,
        PaymentMethod paymentMethod,
        String note,
        String receiptUrl,
        Instant createdAt
) {}

