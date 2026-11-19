package com.tripbudget.tripbudget_core.notification.services;

import com.tripbudget.tripbudget_core.notification.entities.*;
import com.tripbudget.tripbudget_core.notification.enums.NotificationType;
import com.tripbudget.tripbudget_core.notification.repositories.NotificationPreferenceRepository;
import com.tripbudget.tripbudget_core.plan.entities.PlanActivityEntity;
import com.tripbudget.tripbudget_core.plan.enums.ActivityStatus;
import com.tripbudget.tripbudget_core.plan.repositories.PlanActivityRepository;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.enums.*;
import com.tripbudget.tripbudget_core.trip.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationReminderService {
    private final java.time.Clock clock;
    public static final int ACTIVITY_LEAD_MINUTES = 15;
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final NotificationPreferenceRepository preferences;
    private final TripMemberRepository members;
    private final TripRepository trips;
    private final PlanActivityRepository activities;
    private final NotificationService notifications;

    // The current database schedule is read on each scan; no stale in-memory timers/jobs.
    @Transactional
    public void generateDue() {
        Instant now = clock.instant();
        LocalDate today = now.atZone(ZONE).toLocalDate();
        for (NotificationPreferenceEntity preference : preferences.lockReminderPreferences()) {
            for (var member : members.findAllByUserIdAndStatusAndIsDelFalse(preference.getUserId(), TripMemberStatus.ACTIVE)) {
                TripEntity trip = member.getTrip();
                if (trip.isDel() || trip.getStatus() == TripStatus.CANCELLED || trip.getStatus() == TripStatus.COMPLETED) continue;
                Instant scheduled = tripSchedule(trip);
                Instant expires = trip.getStartDate().atStartOfDay(ZONE).toInstant();
                if (due(now, scheduled, expires, member.getJoinedAt(), preference.getRemindersEnabledAt())) {
                    notifications.emit(null, trip, NotificationType.TRIP_REMINDER, List.of(preference.getUserId()), "", trip.getId(),
                        "trip-reminder:" + trip.getId() + ":" + scheduled, expires);
                }
                for (PlanActivityEntity activity : activities.findReminderCandidates(trip.getId(), today, today.plusDays(1))) {
                    Instant start = activityStart(activity);
                    Instant activitySchedule = start.minusSeconds(ACTIVITY_LEAD_MINUTES * 60L);
                    if (due(now, activitySchedule, start, member.getJoinedAt(), preference.getRemindersEnabledAt())) {
                        notifications.emit(null, trip, NotificationType.ACTIVITY_REMINDER, List.of(preference.getUserId()),
                            activity.getTitle(), activity.getId(), "activity-reminder:" + activity.getId() + ":" + activitySchedule, start);
                    }
                }
            }
        }
    }
    public static boolean due(Instant now, Instant scheduled, Instant expires, Instant joined, Instant enabled) {
        return !now.isBefore(scheduled) && now.isBefore(expires) &&
            !joined.isAfter(scheduled) && !enabled.isAfter(scheduled);
    }
    public static Instant tripSchedule(TripEntity trip) {
        return trip.getStartDate().minusDays(3).atTime(19, 0).atZone(ZONE).toInstant();
    }
    private Instant activityStart(PlanActivityEntity activity) {
        return activity.getDay().getPlanDate().atTime(activity.getStartTime()).atZone(ZONE).toInstant();
    }
    @Transactional(readOnly = true)
    public boolean isCurrent(NotificationEntity notification, NotificationPreferenceEntity preference, Instant now) {
        if (notification.getType() != NotificationType.TRIP_REMINDER && notification.getType() != NotificationType.ACTIVITY_REMINDER) return true;
        if (!preference.isPushEnabled()) return false;
        TripEntity trip = trips.findActiveTrip(notification.getTripId());
        if (trip == null || trip.isDel() || trip.getStatus() == TripStatus.CANCELLED || trip.getStatus() == TripStatus.COMPLETED) return false;
        if (notification.getType() == NotificationType.TRIP_REMINDER) {
            if (tripSchedule(trip).isBefore(preference.getRemindersEnabledAt())) return false;
            String key = "trip-reminder:" + trip.getId() + ":" + tripSchedule(trip) + ":" + notification.getRecipientId() + ":TRIP_REMINDER";
            return key.equals(notification.getDedupeKey()) && now.isBefore(trip.getStartDate().atStartOfDay(ZONE).toInstant());
        }
        var activity = activities.findByIdAndTripIdAndIsDelFalse(notification.getTargetId(), trip.getId()).orElse(null);
        if (activity == null || activity.getDay().isDel() || activity.getDay().getPlanDate() == null ||
            activity.getStartTime() == null || activity.getStatus() != ActivityStatus.PLANNED) return false;
        Instant start = activityStart(activity);
        if (start.minusSeconds(ACTIVITY_LEAD_MINUTES * 60L).isBefore(preference.getRemindersEnabledAt())) return false;
        String key = "activity-reminder:" + activity.getId() + ":" + start.minusSeconds(ACTIVITY_LEAD_MINUTES * 60L)
            + ":" + notification.getRecipientId() + ":ACTIVITY_REMINDER";
        return key.equals(notification.getDedupeKey()) && now.isBefore(start);
    }
}
