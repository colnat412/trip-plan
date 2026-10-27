package com.tripbudget.tripbudget_core.settlement.services;

import com.tripbudget.tripbudget_core.trip.services.TripPermissionService;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.expense.entities.ExpenseEntity;
import com.tripbudget.tripbudget_core.expense.entities.ExpenseSplitEntity;
import com.tripbudget.tripbudget_core.expense.enums.ExpenseStatus;
import com.tripbudget.tripbudget_core.expense.repositories.ExpenseRepository;
import com.tripbudget.tripbudget_core.settlement.dtos.request.CreateSettlementRequest;
import com.tripbudget.tripbudget_core.settlement.dtos.response.MemberBalanceResponse;
import com.tripbudget.tripbudget_core.settlement.dtos.response.SettlementResponse;
import com.tripbudget.tripbudget_core.settlement.dtos.response.SuggestedSettlementResponse;
import com.tripbudget.tripbudget_core.settlement.dtos.response.TripSettlementSummaryResponse;
import com.tripbudget.tripbudget_core.settlement.entities.SettlementEntity;
import com.tripbudget.tripbudget_core.settlement.enums.PaymentMethod;
import com.tripbudget.tripbudget_core.settlement.repositories.SettlementRepository;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.entities.TripMemberEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;
import com.tripbudget.tripbudget_core.trip.enums.TripStatus;
import com.tripbudget.tripbudget_core.trip.repositories.TripMemberRepository;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final TripPermissionService permissions;

    private final SettlementRepository settlementRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final HashidsService hashidsService;

    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    @Transactional(readOnly = true)
    public TripSettlementSummaryResponse getSettlementSummary(Long currentUserId, Long tripId) {
        TripEntity trip = getValidTrip(tripId);
        validateMembership(tripId, currentUserId);

        List<TripMemberEntity> members = tripMemberRepository.findAllByTrip_IdAndStatus(tripId, TripMemberStatus.ACTIVE);
        Set<Long> activeUserIds = members.stream()
                .map(TripMemberEntity::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ExpenseEntity> expenses = expenseRepository.findByTripIdAndStatusNotAndIsDelFalseOrderByExpenseDateDesc(
                tripId, ExpenseStatus.DELETED
        );

        List<SettlementEntity> settlements = settlementRepository
                .findByTripIdAndIsDelFalseOrderBySettledAtDescCreatedAtDesc(tripId);

        Map<Long, BigDecimal> totalPaidMap = new HashMap<>();
        Map<Long, BigDecimal> totalShareMap = new HashMap<>();
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (ExpenseEntity exp : expenses) {
            if (exp.isDel()) continue;
            BigDecimal amt = exp.getAmount() != null ? exp.getAmount() : BigDecimal.ZERO;
            totalExpenses = totalExpenses.add(amt);

            Long payer = exp.getPayerId();
            if (payer != null) {
                totalPaidMap.merge(payer, amt, BigDecimal::add);
                activeUserIds.add(payer);
            }

            if (exp.getSplits() != null) {
                for (ExpenseSplitEntity split : exp.getSplits()) {
                    if (split.isDel()) continue;
                    BigDecimal splitAmt = split.getAllocatedAmount() != null ? split.getAllocatedAmount() : BigDecimal.ZERO;
                    Long splitUser = split.getUserId();
                    if (splitUser != null) {
                        totalShareMap.merge(splitUser, splitAmt, BigDecimal::add);
                        activeUserIds.add(splitUser);
                    }
                }
            }
        }

        Map<Long, BigDecimal> settledPaidMap = new HashMap<>();
        Map<Long, BigDecimal> settledReceivedMap = new HashMap<>();
        BigDecimal totalSettled = BigDecimal.ZERO;

        for (SettlementEntity s : settlements) {
            if (s.isDel()) continue;
            BigDecimal amt = s.getAmount() != null ? s.getAmount() : BigDecimal.ZERO;
            totalSettled = totalSettled.add(amt);

            settledPaidMap.merge(s.getPayerId(), amt, BigDecimal::add);
            settledReceivedMap.merge(s.getPayeeId(), amt, BigDecimal::add);
            activeUserIds.add(s.getPayerId());
            activeUserIds.add(s.getPayeeId());
        }

        Map<Long, UserEntity> userMap = getUserMap(activeUserIds);

        Map<Long, BigDecimal> netBalances = new LinkedHashMap<>();
        List<MemberBalanceResponse> memberBalances = new ArrayList<>();

        for (Long uid : activeUserIds) {
            BigDecimal paid = totalPaidMap.getOrDefault(uid, BigDecimal.ZERO);
            BigDecimal share = totalShareMap.getOrDefault(uid, BigDecimal.ZERO);
            BigDecimal sPaid = settledPaidMap.getOrDefault(uid, BigDecimal.ZERO);
            BigDecimal sRecv = settledReceivedMap.getOrDefault(uid, BigDecimal.ZERO);

            // Net = (Paid + Đã trả nợ) - (Phải chịu + Đã nhận trả nợ)
            BigDecimal net = paid.add(sPaid).subtract(share.add(sRecv));
            netBalances.put(uid, net);

            String status;
            if (net.compareTo(EPSILON) > 0) {
                status = "OWED"; // Được nhận lại
            } else if (net.compareTo(EPSILON.negate()) < 0) {
                status = "OWES"; // Phải trả
            } else {
                status = "SETTLED"; // Đã hòa vốn
            }

            UserEntity u = userMap.get(uid);
            memberBalances.add(new MemberBalanceResponse(
                    hashidsService.encode(uid),
                    u != null ? u.getName() : "Member #" + uid,
                    u != null ? u.getEmail() : null,
                    u != null ? u.getAvatarUrl() : null,
                    paid,
                    share,
                    sPaid,
                    sRecv,
                    net,
                    status
            ));
        }

        memberBalances.sort((a, b) -> b.netBalance().compareTo(a.netBalance()));

        List<SuggestedSettlementResponse> suggestedSettlements = computeSuggestedSettlements(
                netBalances, userMap, trip.getBaseCurrency()
        );

        List<SettlementResponse> settlementHistory = settlements.stream()
                .map(s -> mapToSettlementResponse(s, userMap))
                .toList();

        BigDecimal myBalance = netBalances.getOrDefault(currentUserId, BigDecimal.ZERO);
        String myStatus;
        if (myBalance.compareTo(EPSILON) > 0) {
            myStatus = "OWED";
        } else if (myBalance.compareTo(EPSILON.negate()) < 0) {
            myStatus = "OWES";
        } else {
            myStatus = "SETTLED";
        }

        return new TripSettlementSummaryResponse(
                hashidsService.encode(tripId),
                trip.getBaseCurrency(),
                totalExpenses,
                totalSettled,
                myBalance,
                myStatus,
                memberBalances,
                suggestedSettlements,
                settlementHistory
        );
    }

    @Transactional
    public SettlementResponse createSettlement(Long currentUserId, Long tripId, CreateSettlementRequest req) {
        TripEntity trip = getValidTrip(tripId);
        validateMembership(tripId, currentUserId);

        Long payerId = (req.payerId() != null && !req.payerId().isBlank())
                ? hashidsService.decode(req.payerId())
                : currentUserId;
        Long payeeId = hashidsService.decode(req.payeeId());

        var caller = permissions.requireActiveMember(tripId, currentUserId);
        if (!caller.getRole().canManageTrip() && !currentUserId.equals(payerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Members can only record their own payments");
        }

        if (payerId.equals(payeeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payer and Payee cannot be the same member");
        }

        boolean payerIsMember = tripMemberRepository.existsByTrip_IdAndUserIdAndStatus(tripId, payerId, TripMemberStatus.ACTIVE);
        boolean payeeIsMember = tripMemberRepository.existsByTrip_IdAndUserIdAndStatus(tripId, payeeId, TripMemberStatus.ACTIVE);
        if (!payerIsMember || !payeeIsMember) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both payer and payee must be active members of the trip");
        }

        String currency = (req.currency() != null && !req.currency().isBlank())
                ? req.currency().trim().toUpperCase(Locale.ROOT)
                : trip.getBaseCurrency();
        LocalDate settledAt = req.settledAt() != null ? req.settledAt() : LocalDate.now();
        PaymentMethod method = req.paymentMethod() != null ? req.paymentMethod() : PaymentMethod.CASH;

        SettlementEntity settlement = SettlementEntity.create(
                currentUserId,
                tripId,
                payerId,
                payeeId,
                req.amount(),
                currency,
                settledAt,
                method,
                req.note(),
                req.receiptUrl()
        );

        SettlementEntity saved = settlementRepository.save(settlement);
        Map<Long, UserEntity> userMap = getUserMap(Set.of(payerId, payeeId));
        return mapToSettlementResponse(saved, userMap);
    }

    @Transactional
    public void deleteSettlement(Long currentUserId, Long tripId, Long settlementId) {
        getValidTrip(tripId);
        validateMembership(tripId, currentUserId);

        SettlementEntity settlement = settlementRepository.findByIdAndTripIdAndIsDelFalse(settlementId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Settlement transaction not found"));

        var member = permissions.requireActiveMember(tripId, currentUserId);
        boolean ownPayment = currentUserId.equals(settlement.getCreatedBy())
                && currentUserId.equals(settlement.getPayerId());
        if (!member.getRole().canManageTrip() && !ownPayment) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to delete this settlement");
        }

        settlement.markDeleted();
        settlementRepository.save(settlement);
    }

    private List<SuggestedSettlementResponse> computeSuggestedSettlements(
            Map<Long, BigDecimal> netBalances,
            Map<Long, UserEntity> userMap,
            String currency
    ) {
        class BalanceEntry {
            final Long userId;
            BigDecimal amount;

            BalanceEntry(Long userId, BigDecimal amount) {
                this.userId = userId;
                this.amount = amount;
            }
        }

        List<BalanceEntry> creditors = new ArrayList<>();
        List<BalanceEntry> debtors = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : netBalances.entrySet()) {
            BigDecimal balance = entry.getValue();
            if (balance.compareTo(EPSILON) > 0) {
                creditors.add(new BalanceEntry(entry.getKey(), balance));
            } else if (balance.compareTo(EPSILON.negate()) < 0) {
                debtors.add(new BalanceEntry(entry.getKey(), balance.abs()));
            }
        }

        creditors.sort((a, b) -> b.amount.compareTo(a.amount));
        debtors.sort((a, b) -> b.amount.compareTo(a.amount));

        List<SuggestedSettlementResponse> suggested = new ArrayList<>();
        int creditorIndex = 0;
        int debtorIndex = 0;

        while (creditorIndex < creditors.size() && debtorIndex < debtors.size()) {
            BalanceEntry creditor = creditors.get(creditorIndex);
            BalanceEntry debtor = debtors.get(debtorIndex);

            BigDecimal settleAmount = creditor.amount.min(debtor.amount);
            if (settleAmount.compareTo(EPSILON) >= 0) {
                UserEntity fromUser = userMap.get(debtor.userId);
                UserEntity toUser = userMap.get(creditor.userId);

                suggested.add(new SuggestedSettlementResponse(
                        hashidsService.encode(debtor.userId),
                        fromUser != null ? fromUser.getName() : "Member #" + debtor.userId,
                        fromUser != null ? fromUser.getEmail() : null,
                        fromUser != null ? fromUser.getAvatarUrl() : null,
                        hashidsService.encode(creditor.userId),
                        toUser != null ? toUser.getName() : "Member #" + creditor.userId,
                        toUser != null ? toUser.getEmail() : null,
                        toUser != null ? toUser.getAvatarUrl() : null,
                        settleAmount,
                        currency
                ));
            }

            creditor.amount = creditor.amount.subtract(settleAmount);
            debtor.amount = debtor.amount.subtract(settleAmount);

            if (creditor.amount.compareTo(EPSILON) < 0) {
                creditorIndex++;
            }
            if (debtor.amount.compareTo(EPSILON) < 0) {
                debtorIndex++;
            }
        }

        return suggested;
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
                tripId, currentUserId, TripMemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this trip");
        }
    }

    private Map<Long, UserEntity> getUserMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        List<UserEntity> users = userRepository.findAllByIdIn(userIds);
        Map<Long, UserEntity> map = new HashMap<>();
        for (UserEntity u : users) {
            map.put(u.getId(), u);
        }
        return map;
    }

    private SettlementResponse mapToSettlementResponse(SettlementEntity s, Map<Long, UserEntity> userMap) {
        UserEntity payer = userMap.get(s.getPayerId());
        UserEntity payee = userMap.get(s.getPayeeId());

        return new SettlementResponse(
                hashidsService.encode(s.getId()),
                s.getCreatedBy() != null ? hashidsService.encode(s.getCreatedBy()) : null,
                hashidsService.encode(s.getTripId()),
                hashidsService.encode(s.getPayerId()),
                payer != null ? payer.getName() : null,
                payer != null ? payer.getEmail() : null,
                payer != null ? payer.getAvatarUrl() : null,
                hashidsService.encode(s.getPayeeId()),
                payee != null ? payee.getName() : null,
                payee != null ? payee.getEmail() : null,
                payee != null ? payee.getAvatarUrl() : null,
                s.getAmount(),
                s.getCurrency(),
                s.getSettledAt(),
                s.getPaymentMethod(),
                s.getNote(),
                s.getReceiptUrl(),
                s.getCreatedAt()
        );
    }
}
