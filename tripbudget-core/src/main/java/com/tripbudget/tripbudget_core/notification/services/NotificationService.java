package com.tripbudget.tripbudget_core.notification.services;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.plan.repositories.PlanActivityRepository;
import com.tripbudget.tripbudget_core.notification.dtos.NotificationResponse;
import com.tripbudget.tripbudget_core.notification.entities.*;
import com.tripbudget.tripbudget_core.notification.enums.NotificationType;
import com.tripbudget.tripbudget_core.notification.repositories.*;
import com.tripbudget.tripbudget_core.trip.dtos.response.PageResponse;
import com.tripbudget.tripbudget_core.trip.entities.*;
import com.tripbudget.tripbudget_core.trip.enums.*;
import com.tripbudget.tripbudget_core.trip.repositories.*;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notifications;
    private final RealtimeOutboxRepository outbox;
    private final TripMemberRepository members;
    private final TripRepository trips;
    private final UserRepository users;
    private final HashidsService ids;
    private final PlanActivityRepository activities;

    @Transactional(propagation = Propagation.MANDATORY)
    public void memberAdded(Long actorId, TripEntity trip, TripMemberEntity member) {
        UUID eventId = UUID.randomUUID();
        if (member.getStatus() == TripMemberStatus.ACTIVE) {
            record(eventId, member.getUserId(), actorId, trip.getId(), member.getId(), NotificationType.TRIP_MEMBER_ADDED);
        } else {
            record(eventId, member.getUserId(), actorId, trip.getId(), member.getId(), NotificationType.TRIP_INVITATION_PENDING);
            record(eventId, trip.getOwnerId(), actorId, trip.getId(), member.getId(), NotificationType.TRIP_MEMBER_APPROVAL_REQUIRED);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void memberApproved(Long actorId, TripMemberEntity member) {
        record(UUID.randomUUID(), member.getUserId(), actorId, member.getTrip().getId(), member.getId(),
                NotificationType.TRIP_MEMBERSHIP_APPROVED);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void activityCreated(Long actorId, Long tripId, Long activityId, boolean batch) {
        UUID eventId = UUID.randomUUID();
        for (TripMemberEntity member : members.findAllByTrip_IdAndStatus(tripId, TripMemberStatus.ACTIVE)) {
            if (!member.isDel()) {
                record(eventId, member.getUserId(), actorId, tripId, activityId,
                        batch ? NotificationType.TRIP_ACTIVITIES_GENERATED : NotificationType.TRIP_ACTIVITY_CREATED);
            }
        }
    }

    private void record(UUID eventId, Long recipient, Long actor, Long trip, Long target, NotificationType type) {
        if (recipient.equals(actor)) return;
        var notification = notifications.save(NotificationEntity.create(eventId, recipient, actor, trip, target, type));
        outbox.save(RealtimeOutboxEntity.create(eventId, recipient, notification.getId(), "notification.created"));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, int page, int size, boolean unreadOnly) {
        var result = notifications.findVisible(userId, unreadOnly,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        var tripIds = result.stream().map(NotificationEntity::getTripId).collect(Collectors.toSet());
        var actorIds = result.stream().map(NotificationEntity::getActorId).collect(Collectors.toSet());
        var tripMap = trips.findAllById(tripIds).stream().collect(Collectors.toMap(TripEntity::getId, TripEntity::getName));
        var userMap = users.findAllByIdIn(actorIds).stream().collect(Collectors.toMap(u -> u.getId(), u -> u.getName()));
        return PageResponse.from(result.map(n -> new NotificationResponse(ids.encode(n.getId()), n.getType(),
                ids.encode(n.getTripId()), tripMap.get(n.getTripId()), userMap.get(n.getActorId()), n.getCreatedAt(), n.getReadAt())));
    }

    public record Snapshot(PageResponse<NotificationResponse> listing, long unreadCount) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot snapshot(Long userId, int page, int size, boolean unreadOnly) {
        return new Snapshot(list(userId, page, size, unreadOnly), unreadCount(userId));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) { return notifications.countUnread(userId); }

    @Transactional
    public void read(Long userId, Long id) {
        if (id != null && notifications.findVisibleById(userId, id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        if (notifications.markRead(userId, id, Instant.now()) > 0) {
            outbox.save(RealtimeOutboxEntity.create(UUID.randomUUID(), userId, null, "notification.read-state-changed"));
        }
    }

    public record Target(boolean available, String path, String status) {}

    @Transactional(readOnly = true)
    public Target target(Long userId, Long id) {
        var notification = notifications.findVisibleById(userId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        var member = members.findByTrip_IdAndUserIdAndIsDelFalse(notification.getTripId(), userId).orElseThrow();
        if (member.getStatus() != TripMemberStatus.ACTIVE) {
            return new Target(false, null, "INVITED");
        }
        if (notification.getType() == NotificationType.TRIP_ACTIVITY_CREATED
                && !activities.existsByIdAndTripIdAndIsDelFalse(notification.getTargetId(), notification.getTripId())) {
            return new Target(false, null, "UNAVAILABLE");
        }
        if (notification.getType() == NotificationType.TRIP_MEMBER_APPROVAL_REQUIRED) {
            var target = members.findByIdAndTrip_IdAndIsDelFalse(notification.getTargetId(), notification.getTripId());
            if (target.isEmpty() || target.get().getStatus() != TripMemberStatus.INVITED) {
                return new Target(false, null, "UNAVAILABLE");
            }
        }
        return new Target(true, "/plan?tripId=" + ids.encode(notification.getTripId()), "ACTIVE");
    }
}
