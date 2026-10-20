package com.tripbudget.tripbudget_core.expense.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import com.tripbudget.tripbudget_core.expense.enums.ExpenseCategory;
import com.tripbudget.tripbudget_core.expense.enums.ExpenseStatus;
import com.tripbudget.tripbudget_core.expense.enums.SplitType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Getter
@Entity
@Table(
        schema = "trip_core",
        name = "expenses",
        indexes = {
                @Index(name = "idx_expenses_trip_date", columnList = "trip_id, expense_date"),
                @Index(name = "idx_expenses_payer", columnList = "payer_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseEntity extends BaseEntity {

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "payer_id", nullable = false)
    private Long payerId;

    @Column(name = "activity_id")
    private Long activityId;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ExpenseCategory category;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 30)
    private SplitType splitType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpenseStatus status;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    @Column(name = "receipt_upload_id", unique = true)
    private java.util.UUID receiptUploadId;

    public void updateReceipt(java.util.UUID uploadId, String url) {
        this.receiptUploadId = uploadId;
        this.receiptUrl = url;
    }

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseSplitEntity> splits = new ArrayList<>();

    public static ExpenseEntity create(
            Long createdBy,
            Long tripId,
            Long payerId,
            String title,
            ExpenseCategory category,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            SplitType splitType,
            String note,
            String receiptUrl,
            Long activityId
    ) {
        ExpenseEntity expense = new ExpenseEntity();
        expense.createdBy = java.util.Objects.requireNonNull(createdBy, "createdBy is required");
        expense.tripId = tripId;
        expense.payerId = payerId;
        expense.title = title != null ? title.trim() : "";
        expense.category = category != null ? category : ExpenseCategory.OTHER;
        expense.amount = amount != null ? amount : BigDecimal.ZERO;
        expense.currency = currency != null ? currency.trim().toUpperCase(Locale.ROOT) : "VND";
        expense.expenseDate = expenseDate != null ? expenseDate : LocalDate.now();
        expense.splitType = splitType != null ? splitType : SplitType.EQUAL;
        expense.status = ExpenseStatus.CONFIRMED;
        expense.note = note != null && !note.isBlank() ? note.trim() : null;
        expense.receiptUrl = receiptUrl != null && !receiptUrl.isBlank() ? receiptUrl.trim() : null;
        expense.activityId = activityId;
        return expense;
    }

    public static ExpenseEntity create(
            Long createdBy,
            Long tripId,
            Long payerId,
            String title,
            ExpenseCategory category,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            SplitType splitType,
            String note,
            String receiptUrl
    ) {
        return create(createdBy, tripId, payerId, title, category, amount, currency, expenseDate, splitType, note, receiptUrl, null);
    }

    public void update(
            Long payerId,
            String title,
            ExpenseCategory category,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            SplitType splitType,
            String note,
            String receiptUrl,
            Long activityId
    ) {
        if (payerId != null) {
            this.payerId = payerId;
        }
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        if (category != null) {
            this.category = category;
        }
        if (amount != null) {
            this.amount = amount;
        }
        if (currency != null && !currency.isBlank()) {
            this.currency = currency.trim().toUpperCase(Locale.ROOT);
        }
        if (expenseDate != null) {
            this.expenseDate = expenseDate;
        }
        if (splitType != null) {
            this.splitType = splitType;
        }
        this.note = note != null && !note.isBlank() ? note.trim() : null;
        this.receiptUrl = receiptUrl != null && !receiptUrl.isBlank() ? receiptUrl.trim() : null;
        if (activityId != null) {
            this.activityId = activityId;
        }
    }

    public void update(
            Long payerId,
            String title,
            ExpenseCategory category,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            SplitType splitType,
            String note,
            String receiptUrl
    ) {
        update(payerId, title, category, amount, currency, expenseDate, splitType, note, receiptUrl, null);
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public void update(
            String title,
            ExpenseCategory category,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            SplitType splitType,
            String note,
            String receiptUrl
    ) {
        update(null, title, category, amount, currency, expenseDate, splitType, note, receiptUrl);
    }

    public void deleteExpense() {
        this.status = ExpenseStatus.DELETED;
        this.markDeleted();
    }

    public void addSplit(ExpenseSplitEntity split) {
        if (split != null) {
            this.splits.add(split);
        }
    }

    public void clearSplits() {
        this.splits.clear();
    }
}

