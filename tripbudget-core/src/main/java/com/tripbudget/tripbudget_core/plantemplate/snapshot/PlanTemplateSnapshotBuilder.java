package com.tripbudget.tripbudget_core.plantemplate.snapshot;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.plan.entities.PlanActivityEntity;
import com.tripbudget.tripbudget_core.plan.entities.PlanChecklistEntity;
import com.tripbudget.tripbudget_core.plan.entities.PlanDayEntity;
import com.tripbudget.tripbudget_core.plan.repositories.PlanActivityRepository;
import com.tripbudget.tripbudget_core.plan.repositories.PlanChecklistRepository;
import com.tripbudget.tripbudget_core.plan.repositories.PlanDayRepository;
import com.tripbudget.tripbudget_core.plantemplate.dtos.PublishTemplateRequest;
import com.tripbudget.tripbudget_core.plantemplate.dtos.TemplateSnapshot;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PlanTemplateSnapshotBuilder {
    private final PlanDayRepository dayRepository;
    private final PlanActivityRepository activityRepository;
    private final PlanChecklistRepository checklistRepository;
    private final HashidsService hashidsService;
    private final ObjectMapper objectMapper;
    private final TemplateSnapshotValidator snapshotValidator;

    public TemplateSnapshot build(TripEntity sourceTrip, PublishTemplateRequest request) {
        int durationDays = calculateDuration(sourceTrip);
        List<PlanDayEntity> sourceDays = dayRepository
                .findAllByTripIdAndIsDelFalseOrderByDayNumberAsc(sourceTrip.getId());
        List<PlanActivityEntity> sourceActivities = activityRepository
                .findAllByTripIdAndIsDelFalseOrderByOrderIndexAsc(sourceTrip.getId());
        validateActivityCount(sourceActivities);

        Map<Integer, PlanDayEntity> daysByNumber = indexSourceDays(sourceTrip, durationDays, sourceDays);
        Map<Long, List<PlanActivityEntity>> activitiesByDay = groupActivities(sourceDays, sourceActivities);
        List<TemplateSnapshot.Day> publicDays = createPublicDays(
                durationDays, daysByNumber, activitiesByDay, request.includeNotes()
        );
        List<TemplateSnapshot.Checklist> publicChecklists = selectPublicChecklists(
                sourceTrip.getId(), request.checklistIds()
        );
        TemplateSnapshot snapshot = new TemplateSnapshot(
                TemplateSnapshotValidator.SCHEMA_VERSION, publicDays, publicChecklists
        );
        validateSize(snapshot);
        snapshotValidator.validate(snapshot, sourceTrip.getBaseCurrency());
        return snapshot;
    }

    private int calculateDuration(TripEntity sourceTrip) {
        long duration = ChronoUnit.DAYS.between(sourceTrip.getStartDate(), sourceTrip.getEndDate()) + 1;
        if (duration < 1 || duration > TemplateSnapshotValidator.MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TEMPLATE_DURATION_LIMIT_60");
        }
        return (int) duration;
    }

    private void validateActivityCount(List<PlanActivityEntity> activities) {
        if (activities.isEmpty() || activities.size() > TemplateSnapshotValidator.MAX_ACTIVITIES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TEMPLATE_ACTIVITY_LIMIT_1_1000");
        }
    }

    private Map<Integer, PlanDayEntity> indexSourceDays(
            TripEntity sourceTrip, int durationDays, List<PlanDayEntity> sourceDays
    ) {
        Map<Integer, PlanDayEntity> daysByNumber = new HashMap<>();
        for (PlanDayEntity day : sourceDays) {
            int dayNumber = day.getDayNumber();
            LocalDate expectedDate = sourceTrip.getStartDate().plusDays(dayNumber - 1L);
            boolean outsideTrip = dayNumber < 1 || dayNumber > durationDays;
            boolean duplicateDay = daysByNumber.containsKey(dayNumber);
            boolean inconsistentDate = day.getPlanDate() != null && !day.getPlanDate().equals(expectedDate);
            if (outsideTrip || duplicateDay || inconsistentDate) {
                throw invalidSourceDays();
            }
            daysByNumber.put(dayNumber, day);
        }
        return daysByNumber;
    }

    private Map<Long, List<PlanActivityEntity>> groupActivities(
            List<PlanDayEntity> sourceDays, List<PlanActivityEntity> activities
    ) {
        Set<Long> validDayIds = new HashSet<>();
        for (PlanDayEntity day : sourceDays) {
            validDayIds.add(day.getId());
        }
        Map<Long, List<PlanActivityEntity>> activitiesByDay = new HashMap<>();
        for (PlanActivityEntity activity : activities) {
            Long dayId = activity.getDay().getId();
            if (!validDayIds.contains(dayId)) {
                throw invalidSourceDays();
            }
            activitiesByDay.computeIfAbsent(dayId, ignored -> new ArrayList<>()).add(activity);
        }
        Comparator<PlanActivityEntity> activityOrder = Comparator
                .comparing(PlanActivityEntity::getOrderIndex).thenComparing(PlanActivityEntity::getId);
        activitiesByDay.values().forEach(dayActivities -> dayActivities.sort(activityOrder));
        return activitiesByDay;
    }

    private List<TemplateSnapshot.Day> createPublicDays(
            int durationDays, Map<Integer, PlanDayEntity> daysByNumber,
            Map<Long, List<PlanActivityEntity>> activitiesByDay, boolean includeNotes
    ) {
        List<TemplateSnapshot.Day> publicDays = new ArrayList<>();
        for (int dayNumber = 1; dayNumber <= durationDays; dayNumber++) {
            PlanDayEntity sourceDay = daysByNumber.get(dayNumber);
            List<PlanActivityEntity> dayActivities = sourceDay == null
                    ? List.of() : activitiesByDay.getOrDefault(sourceDay.getId(), List.of());
            List<TemplateSnapshot.Activity> publicActivities = dayActivities.stream()
                    .map(activity -> toPublicActivity(activity, includeNotes)).toList();
            String title = sourceDay == null ? null : sourceDay.getTitle();
            String note = sourceDay != null && includeNotes ? sourceDay.getNote() : null;
            publicDays.add(new TemplateSnapshot.Day(dayNumber, title, note, publicActivities));
        }
        return publicDays;
    }

    private TemplateSnapshot.Activity toPublicActivity(PlanActivityEntity activity, boolean includeNotes) {
        if (activity.getEstimatedCost() == null || activity.getEstimatedCost().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ESTIMATED_COST");
        }
        return new TemplateSnapshot.Activity(
                activity.getTitle(), activity.getStartTime(), activity.getEndTime(), activity.getLocation(),
                activity.getCategory(), activity.getEstimatedCost(), activity.getOrderIndex(),
                includeNotes ? activity.getNote() : null
        );
    }

    private List<TemplateSnapshot.Checklist> selectPublicChecklists(Long tripId, List<String> selectedIds) {
        Set<String> remainingIds = new HashSet<>(selectedIds);
        List<TemplateSnapshot.Checklist> publicChecklists = new ArrayList<>();
        for (PlanChecklistEntity item : checklistRepository.findAllByTripIdAndIsDelFalseOrderByCreatedAtAsc(tripId)) {
            if (remainingIds.remove(hashidsService.encode(item.getId()))) {
                publicChecklists.add(new TemplateSnapshot.Checklist(item.getTitle(), item.getCategory()));
            }
        }
        if (!remainingIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CHECKLIST_SELECTION");
        }
        return publicChecklists;
    }

    private void validateSize(TemplateSnapshot snapshot) {
        int bytes = objectMapper.writeValueAsString(snapshot).getBytes(StandardCharsets.UTF_8).length;
        if (bytes > TemplateSnapshotValidator.MAX_SNAPSHOT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TEMPLATE_TOO_LARGE");
        }
    }

    private ResponseStatusException invalidSourceDays() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "SOURCE_DAYS_NEED_CORRECTION");
    }
}
