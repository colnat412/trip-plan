package com.tripbudget.tripbudget_core.ai.dtos.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ScannedReceiptResponse(
        @JsonProperty("merchant_name") String merchantName,
        double amount,
        String currency,
        @JsonProperty("expense_date") String expenseDate,
        String category,
        Double confidence,
        List<ReceiptItemResponse> items,
        @JsonProperty("raw_text") String rawText
) {}
