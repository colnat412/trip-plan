package com.tripbudget.tripbudget_core.settlement.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import com.tripbudget.tripbudget_core.settlement.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

@Getter
@Entity
@Table(
        schema = "trip_core",
        name = "settlements",
        indexes = {
                @Index(name = "idx_settlements_trip", columnList = "trip_id"),
                @Index(name = "idx_settlements_payer", columnList = "payer_id"),
                @Index(name = "idx_settlements_payee", columnList = "payee_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementEntity extends BaseEntity {

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "payer_id", nullable = false)
    private Long payerId;

    @Column(name = "payee_id", nullable = false)
    private Long payeeId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "settled_at", nullable = false)
    private LocalDate settledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    public static SettlementEntity create(
            Long createdBy,
            Long tripId,
            Long payerId,
            Long payeeId,
            BigDecimal amount,
            String currency,
            LocalDate settledAt,
            PaymentMethod paymentMethod,
            String note,
            String receiptUrl
    ) {
        SettlementEntity s = new SettlementEntity();
        s.createdBy = java.util.Objects.requireNonNull(createdBy, "createdBy is required");
        s.tripId = tripId;
        s.payerId = payerId;
        s.payeeId = payeeId;
        s.amount = amount != null ? amount : BigDecimal.ZERO;
        s.currency = currency != null ? currency.trim().toUpperCase(Locale.ROOT) : "VND";
        s.settledAt = settledAt != null ? settledAt : LocalDate.now();
        s.paymentMethod = paymentMethod != null ? paymentMethod : PaymentMethod.CASH;
        s.note = note != null && !note.isBlank() ? note.trim() : null;
        s.receiptUrl = receiptUrl != null && !receiptUrl.isBlank() ? receiptUrl.trim() : null;
        return s;
    }
}

