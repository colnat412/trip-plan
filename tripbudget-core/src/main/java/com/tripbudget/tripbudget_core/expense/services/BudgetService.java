package com.tripbudget.tripbudget_core.expense.services;

import com.tripbudget.tripbudget_core.trip.services.TripPermissionService;

import com.tripbudget.tripbudget_core.expense.dtos.request.SetBudgetRequest;
import com.tripbudget.tripbudget_core.expense.dtos.response.CategoryBreakdownResponse;
import com.tripbudget.tripbudget_core.expense.dtos.response.TripBudgetSummaryResponse;
import com.tripbudget.tripbudget_core.expense.entities.BudgetEntity;
import com.tripbudget.tripbudget_core.expense.entities.CategoryBudgetEntity;
import com.tripbudget.tripbudget_core.expense.enums.ExpenseCategory;
import com.tripbudget.tripbudget_core.expense.enums.ExpenseStatus;
import com.tripbudget.tripbudget_core.expense.repositories.BudgetRepository;
import com.tripbudget.tripbudget_core.expense.repositories.ExpenseRepository;
import com.tripbudget.tripbudget_core.expense.repositories.projections.CategorySpendingTotal;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;
import com.tripbudget.tripbudget_core.trip.enums.TripStatus;
import com.tripbudget.tripbudget_core.trip.repositories.TripMemberRepository;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final TripPermissionService permissions;

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final HashidsService hashidsService;

    @Transactional(readOnly = true)
    public TripBudgetSummaryResponse getBudgetSummary(Long currentUserId, Long tripId) {
        TripEntity trip = getValidTrip(tripId);
        validateMembership(tripId, currentUserId);

        Optional<BudgetEntity> budgetOptional = budgetRepository.findByTripIdAndIsDelFalse(tripId);
        BigDecimal totalBudget = budgetOptional.map(BudgetEntity::getTotalBudget).orElse(BigDecimal.ZERO);
        String currency = budgetOptional.map(BudgetEntity::getCurrency).orElse(trip.getBaseCurrency());

        BigDecimal actualSpent = expenseRepository.sumAmountByTripIdAndStatus(tripId, ExpenseStatus.CONFIRMED);
        if (actualSpent == null) {
            actualSpent = BigDecimal.ZERO;
        }

        BigDecimal remainingBudget = totalBudget.subtract(actualSpent);
        double percentageUsed = calculatePercentage(actualSpent, totalBudget);
        Map<ExpenseCategory, BigDecimal> spentMap = getSpendingByCategory(tripId);

        Map<ExpenseCategory, BigDecimal> limitMap = new HashMap<>();
        if (budgetOptional.isPresent()) {
            for (CategoryBudgetEntity categoryBudget : budgetOptional.get().getCategoryBudgets()) {
                if (!categoryBudget.isDel()) {
                    limitMap.put(categoryBudget.getCategory(), categoryBudget.getLimitAmount());
                }
            }
        }

        List<CategoryBreakdownResponse> breakdownList = new ArrayList<>();
        for (ExpenseCategory category : ExpenseCategory.values()) {
            BigDecimal spent = spentMap.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal limit = limitMap.getOrDefault(category, BigDecimal.ZERO);

            double categoryPercentage = calculatePercentage(spent, limit);

            breakdownList.add(new CategoryBreakdownResponse(
                    category,
                    spent,
                    limit,
                    categoryPercentage
            ));
        }

        breakdownList.sort(this::compareCategoryPriority);

        return new TripBudgetSummaryResponse(
                hashidsService.encode(tripId),
                totalBudget,
                actualSpent,
                remainingBudget,
                percentageUsed,
                currency,
                breakdownList
        );
    }

    @Transactional
    public TripBudgetSummaryResponse setBudget(Long currentUserId, Long tripId, SetBudgetRequest req) {
        TripEntity trip = getValidTrip(tripId);
        permissions.requireManager(tripId, currentUserId);

        BudgetEntity budget = budgetRepository.findByTripIdAndIsDelFalse(tripId)
                .orElseGet(() -> BudgetEntity.create(tripId, req.totalBudget(), req.currency()));

        budget.update(req.totalBudget(), req.currency() != null ? req.currency() : trip.getBaseCurrency());

        if (req.categoryLimits() != null) {
            req.categoryLimits().forEach(budget::setCategoryLimit);
        }

        budgetRepository.save(budget);
        return getBudgetSummary(currentUserId, tripId);
    }

    private double calculatePercentage(BigDecimal spent, BigDecimal limit) {
        if (limit.signum() <= 0) {
            return 0.0;
        }
        return spent.divide(limit, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    private Map<ExpenseCategory, BigDecimal> getSpendingByCategory(Long tripId) {
        Map<ExpenseCategory, BigDecimal> spentByCategory = new EnumMap<>(ExpenseCategory.class);
        for (CategorySpendingTotal total : expenseRepository.sumAmountByCategory(tripId, ExpenseStatus.CONFIRMED)) {
            spentByCategory.put(total.getCategory(), total.getTotalSpent());
        }
        return spentByCategory;
    }

    private int compareCategoryPriority(CategoryBreakdownResponse left, CategoryBreakdownResponse right) {
        boolean leftOverBudget = isOverBudget(left);
        boolean rightOverBudget = isOverBudget(right);
        if (leftOverBudget != rightOverBudget) {
            return leftOverBudget ? -1 : 1;
        }
        if (leftOverBudget) {
            BigDecimal leftExcess = left.spentAmount().subtract(left.limitAmount());
            BigDecimal rightExcess = right.spentAmount().subtract(right.limitAmount());
            return rightExcess.compareTo(leftExcess);
        }
        return right.spentAmount().compareTo(left.spentAmount());
    }

    private boolean isOverBudget(CategoryBreakdownResponse category) {
        return category.limitAmount().signum() > 0
                && category.spentAmount().compareTo(category.limitAmount()) > 0;
    }

    private TripEntity getValidTrip(Long tripId) {
        TripEntity trip = tripRepository.findActiveTrip(tripId);
        if (trip == null || trip.getStatus() == TripStatus.DELETED || trip.isDel()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        return trip;
    }

    private void validateMembership(Long tripId, Long currentUserId) {
        boolean isMember = tripMemberRepository.existsByTrip_IdAndUserIdAndStatus(
                tripId,
                currentUserId,
                TripMemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission in this trip");
        }
    }
}

