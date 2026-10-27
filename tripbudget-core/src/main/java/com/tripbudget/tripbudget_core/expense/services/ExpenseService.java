package com.tripbudget.tripbudget_core.expense.services;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.expense.dtos.request.CreateExpenseRequest;
import com.tripbudget.tripbudget_core.expense.dtos.request.ExpenseFilterRequest;
import com.tripbudget.tripbudget_core.expense.dtos.request.SplitItemRequest;
import com.tripbudget.tripbudget_core.expense.dtos.request.UpdateExpenseRequest;
import com.tripbudget.tripbudget_core.expense.dtos.response.ExpenseResponse;
import com.tripbudget.tripbudget_core.expense.dtos.response.ExpenseSplitResponse;
import com.tripbudget.tripbudget_core.expense.entities.ExpenseEntity;
import com.tripbudget.tripbudget_core.expense.entities.ExpenseSplitEntity;
import com.tripbudget.tripbudget_core.expense.enums.ExpenseStatus;
import com.tripbudget.tripbudget_core.expense.enums.SplitType;
import com.tripbudget.tripbudget_core.expense.repositories.ExpenseRepository;
import com.tripbudget.tripbudget_core.expense.specifications.ExpenseSpecifications;
import com.tripbudget.tripbudget_core.plan.entities.PlanActivityEntity;
import com.tripbudget.tripbudget_core.plan.enums.ActivityStatus;
import com.tripbudget.tripbudget_core.plan.repositories.PlanActivityRepository;
import com.tripbudget.tripbudget_core.trip.dtos.response.PageResponse;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.entities.TripMemberEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;
import com.tripbudget.tripbudget_core.trip.enums.TripStatus;
import com.tripbudget.tripbudget_core.trip.repositories.TripMemberRepository;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import com.tripbudget.tripbudget_core.trip.services.TripPermissionService;
import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final TripPermissionService permissions;

    private final ExpenseRepository expenseRepository;
    private final PlanActivityRepository planActivityRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final HashidsService hashidsService;
    private final ExpenseReceiptService receiptService;

    @Transactional
    public ExpenseResponse createExpense(Long currentUserId, Long tripId, CreateExpenseRequest req) {
        TripEntity trip = getValidTrip(tripId);
        validateWriteMembership(tripId, currentUserId);

        Long payerId = (req.payerId() != null && !req.payerId().isBlank())
                ? hashidsService.decode(req.payerId())
                : currentUserId;
        String currency = (req.currency() != null && !req.currency().isBlank())
                ? req.currency().trim().toUpperCase(Locale.ROOT)
                : trip.getBaseCurrency();

        LocalDate expenseDate = req.expenseDate() != null ? req.expenseDate() : LocalDate.now();
        SplitType splitType = req.splitType() != null ? req.splitType() : SplitType.EQUAL;
        Long activityId = (req.activityId() != null && !req.activityId().isBlank())
                ? hashidsService.decode(req.activityId())
                : null;

        ExpenseEntity expense = ExpenseEntity.create(
                currentUserId,
                tripId,
                payerId,
                req.title(),
                req.category(),
                req.amount(),
                currency,
                expenseDate,
                splitType,
                req.note(),
                null,
                activityId
        );

        applyExpenseSplits(expense, req.amount(), splitType, req.splits(), payerId, tripId, currentUserId);

        receiptService.updateReceipt(currentUserId, expense, req.receiptUploadId(), false, req.receiptUrl());
        ExpenseEntity savedExpense = expenseRepository.save(expense);
        return mapToResponse(savedExpense);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> getTripExpenses(
            Long currentUserId,
            Long tripId,
            int page,
            int size,
            ExpenseFilterRequest filter
    ) {
        getValidTrip(tripId);
        validateMembership(tripId, currentUserId);

        Set<Long> matchedPayerIds = new HashSet<>();
        boolean hasPayerFilter = filter != null && filter.payer() != null && !filter.payer().isBlank();

        if (hasPayerFilter) {
            String keyword = filter.payer().trim();
            matchedPayerIds.addAll(userRepository.findIdsByKeyword(keyword));
            try {
                Long decodedId = hashidsService.decode(keyword);
                if (decodedId != null) {
                    matchedPayerIds.add(decodedId);
                }
            } catch (Exception ignored) {}
        }

        Specification<ExpenseEntity> spec = ExpenseSpecifications.buildSpecification(
                tripId,
                filter,
                matchedPayerIds,
                hasPayerFilter
        );

        Sort sort = ExpenseSpecifications.buildSort(
                filter != null ? filter.sortBy() : null,
                filter != null ? filter.sortDirection() : null
        );

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ExpenseEntity> expensePage = expenseRepository.findAll(spec, pageable);

        List<ExpenseEntity> content = expensePage.getContent();
        Map<Long, UserEntity> userMap = getUserMapForExpenses(content);
        List<ExpenseResponse> items = content.stream()
                .map(e -> mapToResponse(e, userMap))
                .toList();

        PageResponse.Pagination pagination = new PageResponse.Pagination(
                expensePage.getNumber(),
                expensePage.getSize(),
                expensePage.getTotalElements(),
                expensePage.getTotalPages(),
                expensePage.hasNext()
        );

        return new PageResponse<>(items, pagination);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpenseDetail(Long currentUserId, Long tripId, Long expenseId) {
        getValidTrip(tripId);
        validateMembership(tripId, currentUserId);

        ExpenseEntity expense = expenseRepository.findByIdAndTripIdAndIsDelFalse(expenseId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));

        if (expense.getStatus() == ExpenseStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense has been deleted");
        }

        return mapToResponse(expense);
    }

    @Transactional
    public ExpenseResponse updateExpense(Long currentUserId, Long tripId, Long expenseId, UpdateExpenseRequest req) {
        TripEntity trip = getValidTrip(tripId);
        validateWriteMembership(tripId, currentUserId);

        ExpenseEntity expense = expenseRepository.findByIdAndTripIdAndIsDelFalse(expenseId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        permissions.requireManagerOrCreator(tripId, currentUserId, expense.getCreatedBy());

        if (expense.getStatus() == ExpenseStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense has been deleted");
        }

        Long payerId = expense.getPayerId();
        if (req.payerId() != null && !req.payerId().isBlank()) {
            Long decodedPayerId = hashidsService.decode(req.payerId());
            if (decodedPayerId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payerId");
            }
            payerId = decodedPayerId;
        }

        BigDecimal oldAmount = expense.getAmount();
        BigDecimal newAmount = req.amount() != null ? req.amount() : oldAmount;
        SplitType oldSplitType = expense.getSplitType();
        SplitType newSplitType = req.splitType() != null ? req.splitType() : oldSplitType;
        String newCurrency = (req.currency() != null && !req.currency().isBlank())
                ? req.currency().trim().toUpperCase(Locale.ROOT)
                : expense.getCurrency();

        boolean amountChanged = req.amount() != null && req.amount().compareTo(oldAmount) != 0;
        boolean splitTypeChanged = req.splitType() != null && req.splitType() != oldSplitType;

        Long activityId = (req.activityId() != null && !req.activityId().isBlank())
                ? hashidsService.decode(req.activityId())
                : expense.getActivityId();

        expense.update(
                payerId,
                req.title() != null ? req.title() : expense.getTitle(),
                req.category() != null ? req.category() : expense.getCategory(),
                newAmount,
                newCurrency,
                req.expenseDate() != null ? req.expenseDate() : expense.getExpenseDate(),
                newSplitType,
                req.note(),
                expense.getReceiptUrl(),
                activityId
        );

        if (req.splits() != null && !req.splits().isEmpty()) {
            applyExpenseSplits(expense, newAmount, newSplitType, req.splits(), payerId, tripId, currentUserId);
        } else if (splitTypeChanged && newSplitType == SplitType.EQUAL) {
            applyExpenseSplits(expense, newAmount, SplitType.EQUAL, null, payerId, tripId, currentUserId);
        } else if (amountChanged) {
            recalculateExistingSplits(expense, newAmount, payerId);
        }

        receiptService.updateReceipt(currentUserId, expense, req.receiptUploadId(), req.removeReceipt(), req.receiptUrl());
        ExpenseEntity savedExpense = expenseRepository.save(expense);
        return mapToResponse(savedExpense);
    }

    @Transactional
    public void deleteExpense(Long currentUserId, Long tripId, Long expenseId) {
        getValidTrip(tripId);
        validateWriteMembership(tripId, currentUserId);

        ExpenseEntity expense = expenseRepository.findByIdAndTripIdAndIsDelFalse(expenseId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        permissions.requireManagerOrCreator(tripId, currentUserId, expense.getCreatedBy());

        receiptService.updateReceipt(currentUserId, expense, null, true, null);
        expense.deleteExpense();
        expenseRepository.save(expense);

        if (expense.getActivityId() != null) {
            planActivityRepository.findByIdAndTripIdAndIsDelFalse(expense.getActivityId(), tripId).ifPresent(activity -> {
                BigDecimal remainingSpent = expenseRepository.sumAmountByActivityId(tripId, activity.getId());
                if (remainingSpent == null || remainingSpent.compareTo(BigDecimal.ZERO) == 0) {
                    activity.setExpenseId(null);
                    if (activity.getStatus() == ActivityStatus.COMPLETED) {
                        activity.updateStatus(ActivityStatus.PLANNED);
                    }
                } else if (activity.getEstimatedCost() != null && remainingSpent.compareTo(activity.getEstimatedCost()) < 0) {
                    if (activity.getStatus() == ActivityStatus.COMPLETED) {
                        activity.updateStatus(ActivityStatus.PLANNED);
                    }
                }
                planActivityRepository.save(activity);
            });
        }

        List<PlanActivityEntity> linkedActivities = planActivityRepository.findAllByExpenseIdAndIsDelFalse(expenseId);
        for (PlanActivityEntity activity : linkedActivities) {
            if (expense.getActivityId() != null && expense.getActivityId().equals(activity.getId())) {
                continue; // already handled above
            }
            activity.setExpenseId(null);
            if (activity.getStatus() == ActivityStatus.COMPLETED) {
                activity.updateStatus(ActivityStatus.PLANNED);
            }
            planActivityRepository.save(activity);
        }
    }


    private record CalculatedSplit(Long userId, BigDecimal allocatedAmount, BigDecimal splitValue) {}

    private Map<Long, CalculatedSplit> calculateSplits(
            BigDecimal totalAmount, SplitType splitType, List<SplitItemRequest> requestedSplits,
            Long payerId, Long tripId, Long currentUserId
    ) {
        if (splitType == SplitType.EQUAL) {
            return calculateEqualSplits(totalAmount, requestedSplits, payerId, tripId, currentUserId);
        }
        if (splitType == null || requestedSplits == null) {
            return new LinkedHashMap<>();
        }
        return switch (splitType) {
            case EXACT_AMOUNT -> calculateExactSplits(requestedSplits);
            case PERCENTAGE -> calculatePercentageSplits(totalAmount, requestedSplits);
            case SHARE -> calculateShareSplits(totalAmount, requestedSplits);
            default -> new LinkedHashMap<>();
        };
    }

    private Map<Long, CalculatedSplit> calculateEqualSplits(
            BigDecimal totalAmount, List<SplitItemRequest> requestedSplits,
            Long payerId, Long tripId, Long currentUserId
    ) {
        Map<Long, CalculatedSplit> result = new LinkedHashMap<>();
        List<Long> targetUserIds = new ArrayList<>();

        if (requestedSplits != null && !requestedSplits.isEmpty()) {
            for (SplitItemRequest item : requestedSplits) {
                if (item.userId() != null && !item.userId().isBlank()) {
                    Long userId = hashidsService.decode(item.userId());
                    if (userId != null && !targetUserIds.contains(userId)) {
                        targetUserIds.add(userId);
                    }
                }
            }
        }

        if (targetUserIds.isEmpty()) {
            List<TripMemberEntity> members = tripMemberRepository.findAllByTrip_IdAndStatus(tripId, TripMemberStatus.ACTIVE);
            for (TripMemberEntity member : members) {
                targetUserIds.add(member.getUserId());
            }
            if (targetUserIds.isEmpty()) {
                targetUserIds.add(currentUserId);
            }
        }

        int count = targetUserIds.size();
        BigDecimal baseShare = totalAmount.divide(BigDecimal.valueOf(count), 2, RoundingMode.FLOOR);
        BigDecimal totalAllocated = baseShare.multiply(BigDecimal.valueOf(count));
        BigDecimal remainder = totalAmount.subtract(totalAllocated);

        for (Long userId : targetUserIds) {
            BigDecimal userShare = baseShare;
            if (userId.equals(payerId) || (remainder.compareTo(BigDecimal.ZERO) > 0 && userId.equals(targetUserIds.get(0)))) {
                userShare = userShare.add(remainder);
                remainder = BigDecimal.ZERO;
            }

            result.put(userId, new CalculatedSplit(userId, userShare, BigDecimal.ONE));
        }
        return result;
    }

    private Map<Long, CalculatedSplit> calculateExactSplits(List<SplitItemRequest> requestedSplits) {
        Map<Long, CalculatedSplit> result = new LinkedHashMap<>();
        for (SplitItemRequest item : requestedSplits) {
            if (item.userId() != null && !item.userId().isBlank()) {
                Long userId = hashidsService.decode(item.userId());
                if (userId != null) {
                    BigDecimal allocated = item.allocatedAmount() != null ? item.allocatedAmount() : BigDecimal.ZERO;
                    result.put(userId, new CalculatedSplit(userId, allocated, allocated));
                }
            }
        }
        return result;
    }

    private Map<Long, CalculatedSplit> calculatePercentageSplits(
            BigDecimal totalAmount, List<SplitItemRequest> requestedSplits
    ) {
        Map<Long, CalculatedSplit> result = new LinkedHashMap<>();
        record ValidPercentageItem(Long userId, BigDecimal percentage) {}
        List<ValidPercentageItem> validItems = new ArrayList<>();
        for (SplitItemRequest item : requestedSplits) {
            if (item.userId() != null && !item.userId().isBlank()) {
                Long userId = hashidsService.decode(item.userId());
                if (userId != null) {
                    BigDecimal percentage = item.splitValue() != null ? item.splitValue() : BigDecimal.ZERO;
                    validItems.add(new ValidPercentageItem(userId, percentage));
                }
            }
        }

        BigDecimal sumAllocated = BigDecimal.ZERO;
        for (int i = 0; i < validItems.size(); i++) {
            ValidPercentageItem item = validItems.get(i);
            BigDecimal allocated = totalAmount.multiply(item.percentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR);
            sumAllocated = sumAllocated.add(allocated);

            if (i == validItems.size() - 1) {
                BigDecimal diff = totalAmount.subtract(sumAllocated);
                allocated = allocated.add(diff);
            }

            result.put(item.userId(), new CalculatedSplit(item.userId(), allocated, item.percentage()));
        }
        return result;
    }

    private Map<Long, CalculatedSplit> calculateShareSplits(
            BigDecimal totalAmount, List<SplitItemRequest> requestedSplits
    ) {
        Map<Long, CalculatedSplit> result = new LinkedHashMap<>();
        record ValidShareItem(Long userId, BigDecimal share) {}
        List<ValidShareItem> validItems = new ArrayList<>();
        BigDecimal totalShares = BigDecimal.ZERO;
        for (SplitItemRequest item : requestedSplits) {
            if (item.userId() != null && !item.userId().isBlank()) {
                Long userId = hashidsService.decode(item.userId());
                if (userId != null) {
                    BigDecimal share = item.splitValue() != null ? item.splitValue() : BigDecimal.ONE;
                    validItems.add(new ValidShareItem(userId, share));
                    totalShares = totalShares.add(share);
                }
            }
        }

        if (totalShares.compareTo(BigDecimal.ZERO) <= 0) {
            totalShares = BigDecimal.valueOf(Math.max(1, validItems.size()));
        }

        BigDecimal sumAllocated = BigDecimal.ZERO;
        for (int i = 0; i < validItems.size(); i++) {
            ValidShareItem item = validItems.get(i);
            BigDecimal allocated = totalAmount.multiply(item.share())
                    .divide(totalShares, 2, RoundingMode.FLOOR);
            sumAllocated = sumAllocated.add(allocated);

            if (i == validItems.size() - 1) {
                BigDecimal diff = totalAmount.subtract(sumAllocated);
                allocated = allocated.add(diff);
            }

            result.put(item.userId(), new CalculatedSplit(item.userId(), allocated, item.share()));
        }
        return result;
    }

    private void recalculateExistingSplits(ExpenseEntity expense, BigDecimal newTotalAmount, Long payerId) {
        List<ExpenseSplitEntity> splits = expense.getSplits();
        if (splits.isEmpty()) {
            return;
        }

        int count = splits.size();
        SplitType splitType = expense.getSplitType();

        if (splitType == SplitType.EQUAL) {
            BigDecimal baseShare = newTotalAmount.divide(BigDecimal.valueOf(count), 2, RoundingMode.FLOOR);
            BigDecimal totalAllocated = baseShare.multiply(BigDecimal.valueOf(count));
            BigDecimal remainder = newTotalAmount.subtract(totalAllocated);

            for (int i = 0; i < count; i++) {
                ExpenseSplitEntity split = splits.get(i);
                BigDecimal userShare = baseShare;
                if (split.getUserId().equals(payerId) || (remainder.compareTo(BigDecimal.ZERO) > 0 && i == 0)) {
                    userShare = userShare.add(remainder);
                    remainder = BigDecimal.ZERO;
                }
                split.update(userShare, BigDecimal.ONE);
            }
        } else if (splitType == SplitType.PERCENTAGE) {
            BigDecimal sumAllocated = BigDecimal.ZERO;
            for (int i = 0; i < count; i++) {
                ExpenseSplitEntity split = splits.get(i);
                BigDecimal percentage = split.getSplitValue() != null ? split.getSplitValue() : BigDecimal.ZERO;
                BigDecimal allocated = newTotalAmount.multiply(percentage)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR);
                sumAllocated = sumAllocated.add(allocated);

                if (i == count - 1) {
                    BigDecimal diff = newTotalAmount.subtract(sumAllocated);
                    allocated = allocated.add(diff);
                }
                split.update(allocated, percentage);
            }
        } else if (splitType == SplitType.SHARE) {
            BigDecimal totalShares = BigDecimal.ZERO;
            for (ExpenseSplitEntity split : splits) {
                BigDecimal share = split.getSplitValue() != null ? split.getSplitValue() : BigDecimal.ONE;
                totalShares = totalShares.add(share);
            }
            if (totalShares.compareTo(BigDecimal.ZERO) <= 0) {
                totalShares = BigDecimal.valueOf(count);
            }

            BigDecimal sumAllocated = BigDecimal.ZERO;
            for (int i = 0; i < count; i++) {
                ExpenseSplitEntity split = splits.get(i);
                BigDecimal share = split.getSplitValue() != null ? split.getSplitValue() : BigDecimal.ONE;
                BigDecimal allocated = newTotalAmount.multiply(share)
                        .divide(totalShares, 2, RoundingMode.FLOOR);
                sumAllocated = sumAllocated.add(allocated);

                if (i == count - 1) {
                    BigDecimal diff = newTotalAmount.subtract(sumAllocated);
                    allocated = allocated.add(diff);
                }
                split.update(allocated, share);
            }
        }
    }

    private void applyExpenseSplits(
            ExpenseEntity expense,
            BigDecimal totalAmount,
            SplitType splitType,
            List<SplitItemRequest> requestedSplits,
            Long payerId,
            Long tripId,
            Long currentUserId
    ) {
        Map<Long, CalculatedSplit> targetSplitsMap = calculateSplits(
                totalAmount,
                splitType,
                requestedSplits,
                payerId,
                tripId,
                currentUserId
        );

        List<ExpenseSplitEntity> currentSplits = expense.getSplits();

        currentSplits.removeIf(existing -> !targetSplitsMap.containsKey(existing.getUserId()));

        Set<Long> updatedUserIds = new HashSet<>();
        for (ExpenseSplitEntity existing : currentSplits) {
            CalculatedSplit target = targetSplitsMap.get(existing.getUserId());
            if (target != null) {
                existing.update(target.allocatedAmount(), target.splitValue());
                updatedUserIds.add(existing.getUserId());
            }
        }

        for (CalculatedSplit target : targetSplitsMap.values()) {
            if (!updatedUserIds.contains(target.userId())) {
                expense.addSplit(ExpenseSplitEntity.create(
                        expense,
                        target.userId(),
                        target.allocatedAmount(),
                        target.splitValue()
                ));
            }
        }
    }






    private TripEntity getValidTrip(Long tripId) {
        TripEntity trip = tripRepository.findActiveTrip(tripId);
        if (trip == null || trip.getStatus() == TripStatus.DELETED || trip.isDel()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        return trip;
    }

    private void validateWriteMembership(Long tripId, Long currentUserId) {
        permissions.requireActiveMember(tripId, currentUserId);
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

    private Map<Long, UserEntity> getUserMapForExpenses(List<ExpenseEntity> expenses) {
        if (expenses == null || expenses.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> userIds = new HashSet<>();
        for (ExpenseEntity exp : expenses) {
            if (exp.getPayerId() != null) {
                userIds.add(exp.getPayerId());
            }
            if (exp.getSplits() != null) {
                for (ExpenseSplitEntity s : exp.getSplits()) {
                    if (s.getUserId() != null && !s.isDel()) {
                        userIds.add(s.getUserId());
                    }
                }
            }
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserEntity> users = userRepository.findAllByIdIn(userIds);
        Map<Long, UserEntity> map = new HashMap<>();
        for (UserEntity u : users) {
            map.put(u.getId(), u);
        }
        return map;
    }

    private ExpenseResponse mapToResponse(ExpenseEntity entity, Map<Long, UserEntity> userMap) {
        UserEntity payer = userMap != null ? userMap.get(entity.getPayerId()) : null;
        String payerName = payer != null ? payer.getName() : null;
        String payerEmail = payer != null ? payer.getEmail() : null;
        String payerAvatarUrl = payer != null ? payer.getAvatarUrl() : null;

        List<ExpenseSplitResponse> splitResponses = entity.getSplits().stream()
                .filter(s -> !s.isDel())
                .map(s -> {
                    UserEntity splitUser = userMap != null ? userMap.get(s.getUserId()) : null;
                    return new ExpenseSplitResponse(
                            hashidsService.encode(s.getId()),
                            hashidsService.encode(s.getUserId()),
                            splitUser != null ? splitUser.getName() : null,
                            splitUser != null ? splitUser.getEmail() : null,
                            splitUser != null ? splitUser.getAvatarUrl() : null,
                            s.getAllocatedAmount(),
                            s.getSplitValue(),
                            s.isSettled()
                    );
                })
                .toList();

        return new ExpenseResponse(
                hashidsService.encode(entity.getId()),
                entity.getCreatedBy() != null ? hashidsService.encode(entity.getCreatedBy()) : null,
                hashidsService.encode(entity.getTripId()),
                hashidsService.encode(entity.getPayerId()),
                payerName,
                payerEmail,
                payerAvatarUrl,
                entity.getTitle(),
                entity.getCategory(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getExpenseDate(),
                entity.getSplitType(),
                entity.getStatus(),
                entity.getNote(),
                entity.getReceiptUrl(),
                entity.getReceiptUploadId(),
                entity.getActivityId() != null ? hashidsService.encode(entity.getActivityId()) : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                splitResponses
        );
    }

    private ExpenseResponse mapToResponse(ExpenseEntity entity) {
        Map<Long, UserEntity> userMap = getUserMapForExpenses(List.of(entity));
        return mapToResponse(entity, userMap);
    }
}
