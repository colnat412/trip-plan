package com.tripbudget.tripbudget_core.settlement.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import com.tripbudget.tripbudget_core.settlement.enums.PaymentMethod;
import com.tripbudget.tripbudget_core.settlement.enums.SettlementStatus;
import com.tripbudget.tripbudget_core.settlement.enums.SettlementAction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SettlementStatus status = SettlementStatus.PENDING;

    // If the recipient records the payment, the payer must confirm instead.
    public Long confirmationUserId() {
        return payeeId.equals(createdBy) ? payerId : payeeId;
    }

    public void requestDeletion() {
        if (status != SettlementStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed payments can request deletion");
        }
        status = SettlementStatus.DELETE_PENDING;
    }

    public void respond(SettlementAction action) {
        if (status != SettlementStatus.PENDING && status != SettlementStatus.DELETE_PENDING) {
            throw new IllegalStateException("Settlement is no longer awaiting confirmation");
        }
        if (status == SettlementStatus.DELETE_PENDING) {
            if (action == SettlementAction.APPROVE) {
                status = SettlementStatus.DELETED;
                markDeleted();
            } else {
                status = SettlementStatus.CONFIRMED;
            }
        } else {
            status = switch (action) {
                case APPROVE -> SettlementStatus.CONFIRMED;
                case REJECT -> SettlementStatus.REJECTED;
                case CANCEL -> SettlementStatus.CANCELLED;
            };
        }
    }

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
    private LocalDateTime settledAt;

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
            LocalDateTime settledAt,
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
        s.settledAt = settledAt != null ? settledAt : LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        s.paymentMethod = paymentMethod != null ? paymentMethod : PaymentMethod.CASH;
        s.note = note != null && !note.isBlank() ? note.trim() : null;
        s.receiptUrl = receiptUrl != null && !receiptUrl.isBlank() ? receiptUrl.trim() : null;
        return s;
    }
}

