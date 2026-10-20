package com.tripbudget.tripbudget_core.plan.services;

import com.tripbudget.tripbudget_core.trip.services.TripPermissionService;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.expense.entities.ExpenseEntity;
import com.tripbudget.tripbudget_core.expense.enums.ExpenseStatus;
import com.tripbudget.tripbudget_core.expense.repositories.ExpenseRepository;
import com.tripbudget.tripbudget_core.plan.dtos.request.*;
import com.tripbudget.tripbudget_core.plan.dtos.response.*;
import com.tripbudget.tripbudget_core.plan.entities.PlanActivityEntity;
import com.tripbudget.tripbudget_core.plan.entities.PlanActivityLogEntity;
import com.tripbudget.tripbudget_core.plan.entities.PlanChecklistEntity;
import com.tripbudget.tripbudget_core.plan.entities.PlanDayEntity;
import com.tripbudget.tripbudget_core.plan.enums.ActivityLogAction;
import com.tripbudget.tripbudget_core.plan.enums.ActivityStatus;
import com.tripbudget.tripbudget_core.plan.repositories.PlanActivityLogRepository;
import com.tripbudget.tripbudget_core.plan.repositories.PlanActivityRepository;
import com.tripbudget.tripbudget_core.plan.repositories.PlanChecklistRepository;
import com.tripbudget.tripbudget_core.plan.repositories.PlanDayRepository;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;
import com.tripbudget.tripbudget_core.trip.dtos.response.PageResponse;
import com.tripbudget.tripbudget_core.trip.events.PublicTripChangedEvent;
import com.tripbudget.tripbudget_core.trip.repositories.TripMemberRepository;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final TripPermissionService permissions;

    private final PlanDayRepository planDayRepository;
    private final PlanActivityRepository planActivityRepository;
    private final PlanActivityLogRepository planActivityLogRepository;
    private final PlanChecklistRepository planChecklistRepository;
    private final ExpenseRepository expenseRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final HashidsService hashidsService;
    private final ApplicationEventPublisher eventPublisher;

    private TripEntity getActiveMemberTrip(Long tripId, Long currentUserId) {
        TripEntity trip = tripRepository.findActiveTrip(tripId);
        if (trip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }

        boolean isActiveMember = tripMemberRepository.existsByTrip_IdAndUserIdAndStatus(
                tripId,
                currentUserId,
                TripMemberStatus.ACTIVE
        );

        if (!isActiveMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this trip");
        }

        return trip;
    }

    private void assertCanEditPlan(Long tripId, Long currentUserId) {
        permissions.requireActiveMember(tripId, currentUserId);
    }

    @Transactional
    public TripPlanOverviewResponse getTripPlanOverview(Long currentUserId, Long tripId) {
        return getTripPlanOverview(currentUserId, tripId, true);
    }

    @Transactional
    public TripPlanOverviewResponse getTripPlanOverview(Long currentUserId, Long tripId, boolean includeActivities) {
        TripEntity trip = getActiveMemberTrip(tripId, currentUserId);
        return buildTripPlanOverview(trip, includeActivities);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlanActivityResponse> getDayActivities(
            Long currentUserId,
            Long tripId,
            Long dayId,
            int page,
            int size
    ) {
        getActiveMemberTrip(tripId, currentUserId);

        planDayRepository.findByIdAndTripIdAndIsDelFalse(dayId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan day not found"));

        Page<PlanActivityEntity> activityPage = planActivityRepository
                .findPageByDayIdSorted(dayId, PageRequest.of(page, size));

        List<PlanActivityEntity> activities = activityPage.getContent();

        Set<Long> expenseIds = activities.stream()
                .map(PlanActivityEntity::getExpenseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> activitySpentMap = new HashMap<>();
        for (Object[] row : expenseRepository.sumAmountByTripIdAndActivityGrouped(tripId)) {
            Long actId = (Long) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            if (actId != null && sum != null) {
                activitySpentMap.put(actId, sum);
            }
        }

        Map<Long, BigDecimal> activeExpenseAmounts = Collections.emptyMap();
        if (!expenseIds.isEmpty()) {
            activeExpenseAmounts = expenseRepository.findAllById(expenseIds).stream()
                    .filter(e -> !e.isDel() && e.getStatus() != ExpenseStatus.DELETED)
                    .collect(Collectors.toMap(ExpenseEntity::getId, ExpenseEntity::getAmount));
        }

        List<PlanActivityResponse> items = new ArrayList<>();
        for (PlanActivityEntity act : activities) {
            BigDecimal actualSpent = activitySpentMap.get(act.getId());
            if (actualSpent == null && act.getExpenseId() != null) {
                actualSpent = activeExpenseAmounts.get(act.getExpenseId());
            }
            items.add(PlanActivityResponse.from(act, hashidsService, actualSpent));
        }

        PageResponse.Pagination pagination = new PageResponse.Pagination(
                activityPage.getNumber(),
                activityPage.getSize(),
                activityPage.getTotalElements(),
                activityPage.getTotalPages(),
                activityPage.hasNext()
        );

        return new PageResponse<>(items, pagination);
    }

    public TripPlanOverviewResponse buildPublicPlanOverview(TripEntity trip) {
        Long tripId = trip.getId();
        List<PlanDayEntity> days = planDayRepository.findAllByTripIdAndIsDelFalseOrderByDayNumberAsc(tripId);

        BigDecimal totalEstimatedCost = BigDecimal.ZERO;
        int totalActivities = 0;
        int completedActivities = 0;
        List<PlanDayResponse> dayResponses = new ArrayList<>();

        for (PlanDayEntity day : days) {
            List<PlanActivityResponse> actResponses = new ArrayList<>();
            for (PlanActivityEntity act : planActivityRepository.findAllByDayIdSorted(day.getId())) {
                actResponses.add(PlanActivityResponse.publicFrom(act, hashidsService));
                if (act.getEstimatedCost() != null) {
                    totalEstimatedCost = totalEstimatedCost.add(act.getEstimatedCost());
                }
                totalActivities++;
                if (act.getStatus() == ActivityStatus.COMPLETED) {
                    completedActivities++;
                }
            }
            dayResponses.add(PlanDayResponse.from(day, actResponses, hashidsService));
        }

        return new TripPlanOverviewResponse(
                hashidsService.encode(tripId),
                trip.getName(),
                trip.getDestination(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getBaseCurrency(),
                dayResponses.size(),
                totalEstimatedCost,
                totalActivities,
                completedActivities,
                dayResponses,
                Collections.emptyList()
        );
    }

    private void publishPublicTripChanged(Long tripId) {
        eventPublisher.publishEvent(PublicTripChangedEvent.of(tripId));
    }

    private TripPlanOverviewResponse buildTripPlanOverview(TripEntity trip, boolean includeActivities) {
        Long tripId = trip.getId();

        List<PlanDayEntity> days = planDayRepository.findAllByTripIdAndIsDelFalseOrderByDayNumberAsc(tripId);
        if (days.isEmpty()) {
            days = autoGenerateDays(trip);
        }

        BigDecimal totalEstimatedCost = BigDecimal.ZERO;
        int totalActivities = 0;
        int completedActivities = 0;

        List<List<PlanActivityEntity>> dayActivitiesList = new ArrayList<>();
        Set<Long> expenseIds = new HashSet<>();
        for (PlanDayEntity day : days) {
            List<PlanActivityEntity> activities = planActivityRepository
                    .findAllByDayIdSorted(day.getId());
            dayActivitiesList.add(activities);
            for (PlanActivityEntity act : activities) {
                if (act.getExpenseId() != null) {
                    expenseIds.add(act.getExpenseId());
                }
            }
        }

        Map<Long, BigDecimal> activitySpentMap = new HashMap<>();
        List<Object[]> grouped = expenseRepository.sumAmountByTripIdAndActivityGrouped(tripId);
        for (Object[] row : grouped) {
            Long actId = (Long) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            if (actId != null && sum != null) {
                activitySpentMap.put(actId, sum);
            }
        }

        Map<Long, BigDecimal> activeExpenseAmounts = Collections.emptyMap();
        if (!expenseIds.isEmpty()) {
            activeExpenseAmounts = expenseRepository.findAllById(expenseIds).stream()
                    .filter(e -> !e.isDel() && e.getStatus() != ExpenseStatus.DELETED)
                    .collect(Collectors.toMap(ExpenseEntity::getId, ExpenseEntity::getAmount));
        }

        List<PlanDayResponse> dayResponses = new ArrayList<>();
        for (int i = 0; i < days.size(); i++) {
            PlanDayEntity day = days.get(i);
            List<PlanActivityEntity> activities = dayActivitiesList.get(i);
            List<PlanActivityResponse> actResponses = new ArrayList<>();

            for (PlanActivityEntity act : activities) {
                BigDecimal actualSpent = activitySpentMap.get(act.getId());
                if (actualSpent == null && act.getExpenseId() != null) {
                    actualSpent = activeExpenseAmounts.get(act.getExpenseId());
                }

                if (act.getExpenseId() != null && !activeExpenseAmounts.containsKey(act.getExpenseId()) && (actualSpent == null || actualSpent.compareTo(BigDecimal.ZERO) == 0)) {
                    act.setExpenseId(null);
                    if (act.getStatus() == ActivityStatus.COMPLETED) {
                        act.updateStatus(ActivityStatus.PLANNED);
                    }
                    planActivityRepository.save(act);
                } else if (actualSpent != null && actualSpent.compareTo(BigDecimal.ZERO) > 0) {
                    if (act.getEstimatedCost() != null && actualSpent.compareTo(act.getEstimatedCost()) >= 0) {
                        if (act.getStatus() != ActivityStatus.COMPLETED) {
                            act.updateStatus(ActivityStatus.COMPLETED);
                            planActivityRepository.save(act);
                        }
                    } else {
                        if (act.getStatus() == ActivityStatus.COMPLETED && act.getEstimatedCost() != null && act.getEstimatedCost().compareTo(BigDecimal.ZERO) > 0) {
                            act.updateStatus(ActivityStatus.PLANNED);
                            planActivityRepository.save(act);
                        }
                    }
                }

                actResponses.add(PlanActivityResponse.from(act, hashidsService, actualSpent));
                if (act.getEstimatedCost() != null) {
                    totalEstimatedCost = totalEstimatedCost.add(act.getEstimatedCost());
                }
                totalActivities++;
                if (act.getStatus() == ActivityStatus.COMPLETED) {
                    completedActivities++;
                }
            }

            dayResponses.add(includeActivities
                    ? PlanDayResponse.from(day, actResponses, hashidsService)
                    : PlanDayResponse.summary(day, actResponses, hashidsService));
        }

        List<PlanChecklistEntity> checklists = planChecklistRepository
                .findAllByTripIdAndIsDelFalseOrderByCreatedAtAsc(tripId);

        List<PlanChecklistResponse> checklistResponses = mapChecklistResponses(checklists);

        return new TripPlanOverviewResponse(
                hashidsService.encode(trip.getId()),
                trip.getName(),
                trip.getDestination(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getBaseCurrency(),
                dayResponses.size(),
                totalEstimatedCost,
                totalActivities,
                completedActivities,
                dayResponses,
                checklistResponses
        );
    }

    private List<PlanDayEntity> autoGenerateDays(TripEntity trip) {
        List<PlanDayEntity> generated = new ArrayList<>();
        LocalDate start = trip.getStartDate();
        LocalDate end = trip.getEndDate();

        if (start != null && end != null && !end.isBefore(start)) {
            long count = ChronoUnit.DAYS.between(start, end) + 1;
            int maxDays = (int) Math.min(count, 30);
            for (int i = 0; i < maxDays; i++) {
                LocalDate date = start.plusDays(i);
                PlanDayEntity day = PlanDayEntity.create(
                        trip.getId(),
                        i + 1,
                        date,
                        "Ngày " + (i + 1),
                        null
                );
                generated.add(planDayRepository.save(day));
            }
        } else {
            PlanDayEntity day = PlanDayEntity.create(
                    trip.getId(),
                    1,
                    start != null ? start : LocalDate.now(),
                    "Ngày 1",
                    null
            );
            generated.add(planDayRepository.save(day));
        }
        return generated;
    }

    @Transactional
    public PlanDayResponse createPlanDay(Long currentUserId, Long tripId, CreatePlanDayRequest request) {
        getActiveMemberTrip(tripId, currentUserId);

        Optional<PlanDayEntity> existing = planDayRepository
                .findByTripIdAndDayNumberAndIsDelFalse(tripId, request.dayNumber());
        if (existing.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Day number " + request.dayNumber() + " already exists");
        }

        PlanDayEntity day = PlanDayEntity.create(
                tripId,
                request.dayNumber(),
                request.planDate(),
                request.title(),
                request.note()
        );

        PlanDayEntity saved = planDayRepository.save(day);
        publishPublicTripChanged(tripId);
        return PlanDayResponse.from(saved, Collections.emptyList(), hashidsService);
    }

    @Transactional
    public PlanDayResponse updatePlanDay(Long currentUserId, Long tripId, Long dayId, UpdatePlanDayRequest request) {
        getActiveMemberTrip(tripId, currentUserId);

        PlanDayEntity day = planDayRepository.findByIdAndTripIdAndIsDelFalse(dayId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan day not found"));

        day.updateDetails(request.planDate(), request.title(), request.note());

        List<PlanActivityEntity> activities = planActivityRepository
                .findAllByDay_IdAndIsDelFalseOrderByOrderIndexAscStartTimeAsc(day.getId());

        Set<Long> expenseIds = activities.stream()
                .filter(a -> a.getExpenseId() != null)
                .map(PlanActivityEntity::getExpenseId)
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> activitySpentMap = new HashMap<>();
        List<Object[]> grouped = expenseRepository.sumAmountByTripIdAndActivityGrouped(tripId);
        for (Object[] row : grouped) {
            Long actId = (Long) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            if (actId != null && sum != null) {
                activitySpentMap.put(actId, sum);
            }
        }

        Map<Long, BigDecimal> activeExpenseAmounts = Collections.emptyMap();
        if (!expenseIds.isEmpty()) {
            activeExpenseAmounts = expenseRepository.findAllById(expenseIds).stream()
                    .filter(e -> !e.isDel() && e.getStatus() != ExpenseStatus.DELETED)
                    .collect(Collectors.toMap(ExpenseEntity::getId, ExpenseEntity::getAmount));
        }

        List<PlanActivityResponse> actResponses = new ArrayList<>();
        for (PlanActivityEntity a : activities) {
            BigDecimal actualSpent = activitySpentMap.get(a.getId());
            if (actualSpent == null && a.getExpenseId() != null) {
                actualSpent = activeExpenseAmounts.get(a.getExpenseId());
            }
            actResponses.add(PlanActivityResponse.from(a, hashidsService, actualSpent));
        }

        publishPublicTripChanged(tripId);
        return PlanDayResponse.from(day, actResponses, hashidsService);
    }

    @Transactional
    public void deletePlanDay(Long currentUserId, Long tripId, Long dayId) {
        getActiveMemberTrip(tripId, currentUserId);
        permissions.requireManager(tripId, currentUserId);

        PlanDayEntity day = planDayRepository.findByIdAndTripIdAndIsDelFalse(dayId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan day not found"));

        day.markDeleted();

        List<PlanActivityEntity> activities = planActivityRepository
                .findAllByDay_IdAndIsDelFalseOrderByOrderIndexAscStartTimeAsc(dayId);
        for (PlanActivityEntity act : activities) {
            act.markDeleted();
        }
        publishPublicTripChanged(tripId);
    }

    @Transactional
    public void resetDayActivities(Long currentUserId, Long tripId, Long dayId) {
        getActiveMemberTrip(tripId, currentUserId);
        permissions.requireManager(tripId, currentUserId);
        assertCanEditPlan(tripId, currentUserId);

        planDayRepository.findByIdAndTripIdAndIsDelFalse(dayId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan day not found"));

        List<PlanActivityEntity> activities = planActivityRepository
                .findAllByDay_IdAndIsDelFalseOrderByOrderIndexAscStartTimeAsc(dayId);
        for (PlanActivityEntity act : activities) {
            act.markDeleted();
        }
        publishPublicTripChanged(tripId);
    }

    @Transactional
    public PlanActivityResponse createActivity(Long currentUserId, Long tripId, Long dayId, CreateActivityRequest request) {
        getActiveMemberTrip(tripId, currentUserId);
        assertCanEditPlan(tripId, currentUserId);

        PlanDayEntity day = planDayRepository.findByIdAndTripIdAndIsDelFalse(dayId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan day not found"));

        Integer orderIndex = request.orderIndex();
        if (orderIndex == null) {
            List<PlanActivityEntity> currentActivities = planActivityRepository
                    .findAllByDay_IdAndIsDelFalseOrderByOrderIndexAscStartTimeAsc(dayId);
            orderIndex = currentActivities.size();
        }

        PlanActivityEntity activity = PlanActivityEntity.create(
                currentUserId,
                day,
                tripId,
                request.title(),
                request.startTime(),
                request.endTime(),
                request.location(),
                request.category(),
                request.estimatedCost(),
                orderIndex,
                request.note()
        );

        PlanActivityEntity saved = planActivityRepository.save(activity);

        String desc = "Đã tạo hoạt động \"" + saved.getTitle() + "\"";
        if (saved.getLocation() != null && !saved.getLocation().isBlank()) {
            desc += " tại " + saved.getLocation();
        }
        if (saved.getStartTime() != null) {
            desc += " (" + saved.getStartTime() + (saved.getEndTime() != null ? " - " + saved.getEndTime() : "") + ")";
        }

        planActivityLogRepository.save(PlanActivityLogEntity.create(
                tripId,
                dayId,
                saved.getId(),
                currentUserId,
                ActivityLogAction.CREATED,
                saved.getTitle(),
                desc
        ));

        publishPublicTripChanged(tripId);
        return PlanActivityResponse.from(saved, hashidsService);
    }

    @Transactional
    public PlanActivityResponse updateActivity(Long currentUserId, Long tripId, Long activityId, UpdateActivityRequest request) {
        getActiveMemberTrip(tripId, currentUserId);
        assertCanEditPlan(tripId, currentUserId);

        PlanActivityEntity activity = planActivityRepository.findByIdAndTripIdAndIsDelFalse(activityId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));

        if (request.targetDayId() != null && !request.targetDayId().isBlank()) {
            Long newDayId = hashidsService.decode(request.targetDayId());
            if (!activity.getDay().getId().equals(newDayId)) {
                PlanDayEntity newDay = planDayRepository.findByIdAndTripIdAndIsDelFalse(newDayId, tripId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target plan day not found"));
                activity.assignDay(newDay);
            }
        }

        Long expenseId = null;
        if (request.expenseId() != null && !request.expenseId().isBlank()) {
            expenseId = hashidsService.decode(request.expenseId());
        }

        StringBuilder changes = new StringBuilder();
        if (!activity.getTitle().equals(request.title())) {
            changes.append("Đổi tên: \"").append(request.title()).append("\". ");
        }
        if (request.status() != null && activity.getStatus() != request.status()) {
            changes.append("Đổi trạng thái: ").append(request.status() == ActivityStatus.COMPLETED ? "Hoàn thành" : "Dự kiến").append(". ");
        }
        if (request.startTime() != null && !request.startTime().equals(activity.getStartTime())) {
            changes.append("Giờ: ").append(request.startTime()).append(request.endTime() != null ? " - " + request.endTime() : "").append(". ");
        }
        if (request.location() != null && !request.location().equals(activity.getLocation())) {
            changes.append("Địa điểm: ").append(request.location()).append(". ");
        }
        if (request.estimatedCost() != null && !request.estimatedCost().equals(activity.getEstimatedCost())) {
            changes.append("Chi phí: ").append(request.estimatedCost()).append(". ");
        }
        String desc = changes.length() > 0 ? changes.toString().trim() : "Cập nhật chi tiết hoạt động";

        activity.updateDetails(
                request.title(),
                request.startTime(),
                request.endTime(),
                request.location(),
                request.category(),
                request.estimatedCost(),
                request.status(),
                request.orderIndex(),
                request.note(),
                expenseId
        );

        planActivityLogRepository.save(PlanActivityLogEntity.create(
                tripId,
                activity.getDay().getId(),
                activity.getId(),
                currentUserId,
                ActivityLogAction.UPDATED,
                activity.getTitle(),
                desc
        ));

        publishPublicTripChanged(tripId);

        BigDecimal actualSpent = resolveActualSpent(tripId, activity);
        return PlanActivityResponse.from(activity, hashidsService, actualSpent);
    }

    @Transactional
    public PlanActivityResponse updateActivityStatus(Long currentUserId, Long tripId, Long activityId, ActivityStatus status) {
        getActiveMemberTrip(tripId, currentUserId);
        assertCanEditPlan(tripId, currentUserId);

        PlanActivityEntity activity = planActivityRepository.findByIdAndTripIdAndIsDelFalse(activityId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));

        activity.updateStatus(status);

        String statusLabel = status == ActivityStatus.COMPLETED ? "Đã hoàn thành" : "Dự kiến";
        planActivityLogRepository.save(PlanActivityLogEntity.create(
                tripId,
                activity.getDay().getId(),
                activity.getId(),
                currentUserId,
                ActivityLogAction.STATUS_CHANGED,
                activity.getTitle(),
                "Đổi trạng thái sang: " + statusLabel
        ));

        publishPublicTripChanged(tripId);

        BigDecimal actualSpent = resolveActualSpent(tripId, activity);
        return PlanActivityResponse.from(activity, hashidsService, actualSpent);
    }

    private BigDecimal resolveActualSpent(Long tripId, PlanActivityEntity activity) {
        BigDecimal sum = expenseRepository.sumAmountByActivityId(tripId, activity.getId());
        if (sum != null && sum.compareTo(BigDecimal.ZERO) > 0) {
            return sum;
        }
        if (activity.getExpenseId() != null) {
            return expenseRepository.findByIdAndTripIdAndIsDelFalse(activity.getExpenseId(), tripId)
                    .map(ExpenseEntity::getAmount)
                    .orElse(null);
        }
        return null;
    }

    @Transactional
    public void deleteActivity(Long currentUserId, Long tripId, Long activityId) {
        getActiveMemberTrip(tripId, currentUserId);
        assertCanEditPlan(tripId, currentUserId);

        PlanActivityEntity activity = planActivityRepository.findByIdAndTripIdAndIsDelFalse(activityId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));

        permissions.requireManagerOrCreator(tripId, currentUserId, activity.getCreatedBy());
        activity.markDeleted();

        planActivityLogRepository.save(PlanActivityLogEntity.create(
                tripId,
                activity.getDay().getId(),
                activity.getId(),
                currentUserId,
                ActivityLogAction.DELETED,
                activity.getTitle(),
                "Đã xóa hoạt động \"" + activity.getTitle() + "\" khỏi lịch trình"
        ));
        publishPublicTripChanged(tripId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlanActivityLogResponse> getActivityLogs(Long currentUserId, Long tripId, int page, int size) {
        getActiveMemberTrip(tripId, currentUserId);

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Page<PlanActivityLogEntity> logPage = planActivityLogRepository
                .findAllByTripIdAndIsDelFalse(tripId, PageRequest.of(page, size, sort));
        List<PlanActivityLogEntity> logs = logPage.getContent();

        Set<Long> userIds = logs.stream()
                .map(PlanActivityLogEntity::getUserId)
                .collect(Collectors.toSet());

        Map<Long, UserEntity> userMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            userMap = userRepository.findAllByIdIn(userIds).stream()
                    .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        }

        List<PlanActivityLogResponse> responses = new ArrayList<>();
        for (PlanActivityLogEntity log : logs) {
            UserEntity user = userMap.get(log.getUserId());
            responses.add(PlanActivityLogResponse.from(log, user, hashidsService));
        }

        PageResponse.Pagination pagination = new PageResponse.Pagination(
                logPage.getNumber(),
                logPage.getSize(),
                logPage.getTotalElements(),
                logPage.getTotalPages(),
                logPage.hasNext()
        );

        return new PageResponse<>(responses, pagination);
    }

    @Transactional(readOnly = true)
    public List<PlanChecklistResponse> getChecklists(Long currentUserId, Long tripId) {
        getActiveMemberTrip(tripId, currentUserId);

        List<PlanChecklistEntity> checklists = planChecklistRepository
                .findAllByTripIdAndIsDelFalseOrderByCreatedAtAsc(tripId);

        return mapChecklistResponses(checklists);
    }

    @Transactional
    public PlanChecklistResponse createChecklist(Long currentUserId, Long tripId, CreateChecklistRequest request) {
        getActiveMemberTrip(tripId, currentUserId);

        Long assigneeId = null;
        if (request.assigneeId() != null && !request.assigneeId().isBlank()) {
            assigneeId = hashidsService.decode(request.assigneeId());
        }

        PlanChecklistEntity item = PlanChecklistEntity.create(
                tripId,
                request.title(),
                request.category(),
                assigneeId
        );

        PlanChecklistEntity saved = planChecklistRepository.save(item);

        UserEntity assignee = null;
        if (assigneeId != null) {
            assignee = userRepository.findById(assigneeId).orElse(null);
        }

        return PlanChecklistResponse.from(saved, assignee, hashidsService);
    }

    @Transactional
    public PlanChecklistResponse updateChecklist(Long currentUserId, Long tripId, Long checklistId, UpdateChecklistRequest request) {
        getActiveMemberTrip(tripId, currentUserId);

        PlanChecklistEntity item = planChecklistRepository.findByIdAndTripIdAndIsDelFalse(checklistId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist item not found"));

        Long assigneeId = item.getAssigneeId();
        if (request.assigneeId() != null) {
            assigneeId = request.assigneeId().isBlank() ? null : hashidsService.decode(request.assigneeId());
        }

        item.updateDetails(request.title(), request.category(), request.isCompleted(), assigneeId);

        UserEntity assignee = null;
        if (assigneeId != null) {
            assignee = userRepository.findById(assigneeId).orElse(null);
        }

        return PlanChecklistResponse.from(item, assignee, hashidsService);
    }

    @Transactional
    public PlanChecklistResponse toggleChecklist(Long currentUserId, Long tripId, Long checklistId) {
        getActiveMemberTrip(tripId, currentUserId);

        PlanChecklistEntity item = planChecklistRepository.findByIdAndTripIdAndIsDelFalse(checklistId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist item not found"));

        item.toggle();

        UserEntity assignee = null;
        if (item.getAssigneeId() != null) {
            assignee = userRepository.findById(item.getAssigneeId()).orElse(null);
        }

        return PlanChecklistResponse.from(item, assignee, hashidsService);
    }

    @Transactional
    public void deleteChecklist(Long currentUserId, Long tripId, Long checklistId) {
        getActiveMemberTrip(tripId, currentUserId);

        PlanChecklistEntity item = planChecklistRepository.findByIdAndTripIdAndIsDelFalse(checklistId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist item not found"));

        item.markDeleted();
    }

    private List<PlanChecklistResponse> mapChecklistResponses(List<PlanChecklistEntity> checklists) {
        Set<Long> userIds = checklists.stream()
                .map(PlanChecklistEntity::getAssigneeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, UserEntity> userMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            userMap = userRepository.findAllByIdIn(userIds).stream()
                    .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        }

        List<PlanChecklistResponse> responses = new ArrayList<>();
        for (PlanChecklistEntity item : checklists) {
            UserEntity assignee = item.getAssigneeId() != null ? userMap.get(item.getAssigneeId()) : null;
            responses.add(PlanChecklistResponse.from(item, assignee, hashidsService));
        }
        return responses;
    }
}

