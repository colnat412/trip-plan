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

        Optional<BudgetEntity> budgetOpt = budgetRepository.findByTripIdAndIsDelFalse(tripId);
        BigDecimal totalBudget = budgetOpt.map(BudgetEntity::getTotalBudget).orElse(BigDecimal.ZERO);
        String currency = budgetOpt.map(BudgetEntity::getCurrency).orElse(trip.getBaseCurrency());

        BigDecimal actualSpent = expenseRepository.sumAmountByTripIdAndStatus(tripId, ExpenseStatus.CONFIRMED);
        if (actualSpent == null) {
            actualSpent = BigDecimal.ZERO;
        }

        BigDecimal remainingBudget = totalBudget.subtract(actualSpent);
        double percentageUsed = 0.0;
        if (totalBudget.compareTo(BigDecimal.ZERO) > 0) {
            percentageUsed = actualSpent.divide(totalBudget, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        // Lấy chi tiêu theo từng danh mục
        List<Object[]> categorySums = expenseRepository.sumAmountByCategory(tripId, ExpenseStatus.CONFIRMED);
        Map<ExpenseCategory, BigDecimal> spentMap = new HashMap<>();
        for (Object[] row : categorySums) {
            ExpenseCategory cat = (ExpenseCategory) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            spentMap.put(cat, sum);
        }

        Map<ExpenseCategory, BigDecimal> limitMap = new HashMap<>();
        if (budgetOpt.isPresent()) {
            for (CategoryBudgetEntity cb : budgetOpt.get().getCategoryBudgets()) {
                if (!cb.isDel()) {
                    limitMap.put(cb.getCategory(), cb.getLimitAmount());
                }
            }
        }

        List<CategoryBreakdownResponse> breakdownList = new ArrayList<>();
        for (ExpenseCategory cat : ExpenseCategory.values()) {
            BigDecimal spent = spentMap.getOrDefault(cat, BigDecimal.ZERO);
            BigDecimal limit = limitMap.getOrDefault(cat, BigDecimal.ZERO);

            double catPercentage = 0.0;
            if (limit.compareTo(BigDecimal.ZERO) > 0) {
                catPercentage = spent.divide(limit, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }

            breakdownList.add(new CategoryBreakdownResponse(
                    cat,
                    spent,
                    limit,
                    catPercentage
            ));
        }

        breakdownList.sort((a, b) -> {
            boolean aOver = a.limitAmount().compareTo(BigDecimal.ZERO) > 0 && a.spentAmount().compareTo(a.limitAmount()) > 0;
            boolean bOver = b.limitAmount().compareTo(BigDecimal.ZERO) > 0 && b.spentAmount().compareTo(b.limitAmount()) > 0;
            if (aOver && !bOver) return -1;
            if (!aOver && bOver) return 1;
            if (aOver && bOver) {
                BigDecimal aExcess = a.spentAmount().subtract(a.limitAmount());
                BigDecimal bExcess = b.spentAmount().subtract(b.limitAmount());
                return bExcess.compareTo(aExcess);
            }
            return b.spentAmount().compareTo(a.spentAmount());
        });

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

