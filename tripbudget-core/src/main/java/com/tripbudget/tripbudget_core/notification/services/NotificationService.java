package com.tripbudget.tripbudget_core.notification.services;

import com.tripbudget.tripbudget_core.notification.dtos.request.*;
import com.tripbudget.tripbudget_core.notification.dtos.response.*;
import com.tripbudget.tripbudget_core.notification.entities.*;
import com.tripbudget.tripbudget_core.notification.enums.NotificationType;
import com.tripbudget.tripbudget_core.notification.enums.NotificationAccessStatus;
import com.tripbudget.tripbudget_core.notification.repositories.*;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.entities.TripMemberEntity;
import com.tripbudget.tripbudget_core.trip.enums.*;
import com.tripbudget.tripbudget_core.trip.repositories.TripMemberRepository;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final java.time.Clock clock;
    private final com.tripbudget.tripbudget_core.plan.repositories.PlanActivityRepository activities;
    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final PushDeviceRepository devices;
    private final NotificationPreferenceRepository preferences;
    private final TripMemberRepository members;
    private final com.tripbudget.tripbudget_core.trip.repositories.TripRepository trips;
    private final UserRepository users;
    private final HashidsService hashids;
    private final ApplicationEventPublisher events;
    @Value("${notifications.enabled:false}") private boolean enabled;

    public record WorkAvailable() {}

    // Runs inside the calling business transaction: inbox and delivery are the durable outbox.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void emit(Long actorId, TripEntity trip, NotificationType type, Collection<Long> recipients,
                     String subject, Long targetId, String eventKey, Instant expiresAt) {
        if (!enabled) return;
        Instant now = clock.instant();
        for (Long recipient : new LinkedHashSet<>(recipients)) {
            if (recipient == null || recipient.equals(actorId) || !canReceive(recipient, trip.getId(), type)) continue;
            String key = eventKey + ":" + recipient + ":" + type;
            if (notifications.existsByDedupeKey(key)) continue;
            NotificationEntity notification = notifications.save(NotificationEntity.create(
                recipient, trip.getId(), trip.getName(), type, subject, targetId, key, expiresAt));
            NotificationPreferenceEntity preference = preference(recipient);
            if (type != NotificationType.MEMBER_PENDING && preference.isPushEnabled()) {
                for (PushDeviceEntity device : devices.findByUserIdAndEnabledTrue(recipient)) {
                    deliveries.save(NotificationDeliveryEntity.create(notification.getId(), device, now));
                }
            }
        }
        events.publishEvent(new WorkAvailable());
    }

    @Transactional(readOnly = true)
    public boolean canReceive(Long userId, Long tripId, NotificationType type) {
        if (users.findById(userId).filter(u -> "ACTIVE".equals(u.getStatus())).isEmpty()) return false;
        var member = members.findByTrip_IdAndUserId(tripId, userId).orElse(null);
        if (member == null) return false;
        if (type == NotificationType.MEMBER_REMOVED) return true;
        if (member.isDel() || member.getTrip().isDel()) return false;
        if (type == NotificationType.MEMBER_INVITATION_ACCEPTED || type == NotificationType.MEMBER_INVITATION_DECLINED
            || type == NotificationType.MEMBER_INVITATION_CANCELLED) return false;
        if (type == NotificationType.MEMBER_PENDING || type == NotificationType.MEMBER_INVITED) return member.getStatus() == TripMemberStatus.INVITED;
        return member.getStatus() == TripMemberStatus.ACTIVE;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void memberAdded(Long actorId, TripEntity trip, TripMemberEntity member) {
        if (!enabled) return;
        String key = UUID.randomUUID().toString();
        if (member.getStatus() == TripMemberStatus.ACTIVE) {
            emit(actorId, trip, NotificationType.MEMBER_ADDED, List.of(member.getUserId()), "", member.getId(), key, null);
        } else if (member.getStatus() == TripMemberStatus.INVITED) {
            emit(actorId, trip, NotificationType.MEMBER_PENDING, List.of(member.getUserId()), "", member.getId(), key, null);
            List<Long> approvers = members.findAllByTrip_IdAndStatus(trip.getId(), TripMemberStatus.ACTIVE).stream()
                .filter(m -> !m.isDel() && (m.getRole() == TripMemberRole.OWNER ||
                    (m.getRole() == TripMemberRole.VICE && member.getRole() == TripMemberRole.MEMBER)))
                .map(TripMemberEntity::getUserId).toList();
            emit(actorId, trip, NotificationType.MEMBER_APPROVAL_REQUIRED, approvers, "", member.getId(), key, null);
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void memberInvited(Long actorId, TripEntity trip, TripMemberEntity member) {
        requireEnabled();
        cancelInvitations(member.getId());
        emit(actorId, trip, NotificationType.MEMBER_INVITED, List.of(member.getUserId()), "", member.getId(), UUID.randomUUID().toString(), null);
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void cancelInvitations(Long memberId) {
        notifications.findByTargetIdAndType(memberId, NotificationType.MEMBER_INVITED).forEach(NotificationEntity::cancelInvitation);
    }
    @Transactional(readOnly = true)
    public boolean hasRecipientInvitation(Long memberId) {
        return notifications.existsByTargetIdAndType(memberId, NotificationType.MEMBER_INVITED);
    }
    @Transactional(readOnly = true)
    public Set<Long> pendingInvitationMemberIds(Long tripId) {
        return notifications.pendingInvitationMemberIds(tripId);
    }
    @Transactional
    public NotificationResponse respondToInvitation(Long userId, Long id, boolean accept) {
        requireEnabled();
        Long tripId = notifications.ownedTripId(userId, id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
        // All member mutations lock the trip first: serialize accept/decline/revoke/reinvite.
        TripEntity trip = trips.findByIdForUpdate(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        NotificationEntity invitation = notifications.lockById(id)
            .filter(n -> !n.isDel() && n.getRecipientId().equals(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
        NotificationType resolved = accept ? NotificationType.MEMBER_INVITATION_ACCEPTED : NotificationType.MEMBER_INVITATION_DECLINED;
        if (invitation.getType() == resolved) return detail(userId, id); // same decision is idempotent
        if (invitation.getType() != NotificationType.MEMBER_INVITED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation is no longer pending");
        }
        TripMemberEntity member = members.findByTrip_IdAndUserId(tripId, userId).orElse(null);
        if (trip.isDel() || trip.getStatus() == TripStatus.DELETED || member == null || member.isDel()
            || member.getStatus() != TripMemberStatus.INVITED || !member.getId().equals(invitation.getTargetId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation is no longer available");
        }
        if (accept) member.updateStatus(TripMemberStatus.ACTIVE);
        else member.leave(); // inactive relationship; the notification records the distinct DECLINED outcome
        invitation.resolveInvitation(accept, clock.instant());
        return response(invitation, member);
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse inbox(Long userId, String cursor, int limit, boolean unreadOnly) {
        requireEnabled();
        if (limit < 1 || limit > 50) throw badRequest("Invalid limit");
        Instant before = Instant.EPOCH;
        Long beforeId = 0L;
        boolean hasCursor = cursor != null && !cursor.isBlank();
        if (hasCursor) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = decoded.split("\\|", 2);
                before = Instant.parse(parts[0]);
                beforeId = Long.valueOf(parts[1]);
            } catch (RuntimeException exception) { throw badRequest("Invalid cursor"); }
        }
        Instant through = clock.instant();
        List<NotificationEntity> rows = notifications.inbox(userId, before, beforeId, hasCursor, unreadOnly, PageRequest.of(0, limit + 1));
        boolean more = rows.size() > limit;
        List<NotificationEntity> page = rows.subList(0, Math.min(rows.size(), limit));
        String next = null;
        if (more) {
            NotificationEntity last = page.getLast();
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (last.getCreatedAt() + "|" + last.getId()).getBytes(StandardCharsets.UTF_8));
        }
        // One membership query for the page, including former members.
        Map<Long, TripMemberEntity> membershipByTrip = new HashMap<>();
        if (!page.isEmpty()) {
            members.findByUserIdAndTrip_IdIn(userId, page.stream().map(NotificationEntity::getTripId).distinct().toList())
                .forEach(member -> membershipByTrip.put(member.getTrip().getId(), member));
        }
        return new NotificationPageResponse(page.stream()
            .map(n -> response(n, membershipByTrip.get(n.getTripId()))).toList(), next, through);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unread(Long userId) {
        requireEnabled();
        return new UnreadCountResponse(notifications.unread(userId));
    }
    @Transactional(readOnly = true)
    public NotificationResponse detail(Long userId, Long id) {
        requireEnabled();
        NotificationEntity notification = accessible(userId, id);
        TripMemberEntity member = members.findByTrip_IdAndUserId(notification.getTripId(), userId).orElse(null);
        return response(notification, member);
    }
    @Transactional
    public void deleteNotification(Long userId, Long id) {
        requireEnabled();
        NotificationEntity notification = notifications.findById(id)
            .filter(item -> item.getRecipientId().equals(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.markDeleted();
    }
    @Transactional
    public void markRead(Long userId, Long id) {
        requireEnabled();
        accessible(userId, id).markRead(clock.instant());
    }
    @Transactional
    public void readAll(Long userId, Instant through) {
        requireEnabled();
        Instant now = clock.instant();
        if (through.isAfter(now)) throw badRequest("Invalid read boundary");
        notifications.readAll(userId, through, now);
    }
    private NotificationEntity accessible(Long userId, Long id) {
        return notifications.accessible(userId, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }
    private NotificationAccessStatus accessStatus(TripMemberEntity member) {
        if (member == null) return NotificationAccessStatus.UNAVAILABLE;
        if (member.getTrip().isDel() || member.getTrip().getStatus() == TripStatus.DELETED) return NotificationAccessStatus.TRIP_DELETED;
        if (member.getStatus() == TripMemberStatus.LEFT) return NotificationAccessStatus.LEFT;
        if (member.getStatus() == TripMemberStatus.REMOVED || member.isDel()) return NotificationAccessStatus.REMOVED;
        if (member.getStatus() == TripMemberStatus.INVITED) return NotificationAccessStatus.PENDING;
        return member.getStatus() == TripMemberStatus.ACTIVE ? NotificationAccessStatus.AVAILABLE : NotificationAccessStatus.UNAVAILABLE;
    }
    private NotificationResponse response(NotificationEntity n, TripMemberEntity member) {
        NotificationAccessStatus access = accessStatus(member);
        String invitationStatus = switch (n.getType()) {
            case MEMBER_INVITED -> member != null && !member.isDel() && !member.getTrip().isDel()
                && member.getTrip().getStatus() != TripStatus.DELETED && member.getStatus() == TripMemberStatus.INVITED
                && member.getId().equals(n.getTargetId()) ? "PENDING" : "CANCELLED";
            case MEMBER_INVITATION_ACCEPTED -> "ACCEPTED";
            case MEMBER_INVITATION_DECLINED -> "DECLINED";
            case MEMBER_INVITATION_CANCELLED -> "CANCELLED";
            default -> null;
        };
        NotificationType responseType = n.getType() == NotificationType.MEMBER_INVITED && "CANCELLED".equals(invitationStatus)
            ? NotificationType.MEMBER_INVITATION_CANCELLED : n.getType();
        // Keep the historical event/name, but never return private target links after access ends.
        boolean minimal = n.getType().minimalAccess() || access != NotificationAccessStatus.AVAILABLE;
        String dayId = null;
        if (!minimal && n.getType() == NotificationType.ACTIVITY_REMINDER && n.getTargetId() != null) {
            dayId = activities.findByIdAndTripIdAndIsDelFalse(n.getTargetId(), n.getTripId())
                .filter(a -> !a.getDay().isDel()).map(a -> hashids.encode(a.getDay().getId())).orElse(null);
        }
        return new NotificationResponse(hashids.encode(n.getId()), responseType.name(), minimal ? null : hashids.encode(n.getTripId()),
            n.getTripName(), minimal ? "" : n.getSubject(),
            minimal || n.getTargetId() == null ? null : hashids.encode(n.getTargetId()), n.getCreatedAt(), n.getReadAt(), dayId, access, invitationStatus);
    }
    @Transactional(readOnly = true)
    public NotificationPreferenceEntity preference(Long userId) {
        return preferences.findById(userId).orElseGet(() -> NotificationPreferenceEntity.defaults(userId));
    }
    @Transactional
    public NotificationPreferenceResponse getPreferences(Long userId) {
        requireEnabled();
        users.findForUpdateById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return preferenceResponse(preferences.findById(userId)
            .orElseGet(() -> preferences.save(NotificationPreferenceEntity.defaults(userId))));
    }
    @Transactional
    public NotificationPreferenceResponse updatePreferences(Long userId, NotificationPreferenceRequest request) {
        requireEnabled();
        users.findForUpdateById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        NotificationPreferenceEntity preference = preference(userId);
        preference.update(request.pushEnabled(), request.locale(), clock.instant());
        return preferenceResponse(preferences.save(preference));
    }
    private NotificationPreferenceResponse preferenceResponse(NotificationPreferenceEntity p) {
        return new NotificationPreferenceResponse(p.isPushEnabled(), p.getLocale());
    }
    @Transactional
    public PushDeviceResponse registerDevice(Long userId, String sessionId, PushDeviceRequest request) {
        requireEnabled();
        users.findForUpdateById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PushDeviceEntity device = devices.findByInstallationId(request.installationId())
            .orElseGet(() -> PushDeviceEntity.create(request.installationId()));
        var addressOwner = devices.findByAddressValue(request.addressValue()).orElse(null);
        if (addressOwner != null && !addressOwner.getId().equals(device.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Push address is already registered");
        }
        // Generation prevents queued work for the old account/session from following a remapped device.
        boolean same = device.isEnabled() && userId.equals(device.getUserId()) && sessionId.equals(device.getSessionId())
            && request.addressType().equals(device.getAddressType()) && request.addressValue().equals(device.getAddressValue());
        if (!same) device.register(userId, sessionId, request.addressType(), request.addressValue(), clock.instant());
        else device.seen(clock.instant());
        return new PushDeviceResponse(hashids.encode(devices.save(device).getId()));
    }
    @Transactional
    public void revokeDevice(Long userId, Long id) {
        requireEnabled();
        PushDeviceEntity device = devices.lockById(id).filter(d -> d.getUserId().equals(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        device.revoke();
    }
    @Transactional
    public void revokeSession(Long userId, String sessionId) {
        if (!enabled) return;
        devices.findByUserIdAndSessionIdAndEnabledTrue(userId, sessionId).forEach(PushDeviceEntity::revoke);
    }
    private void requireEnabled() {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Notifications are disabled");
    }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
