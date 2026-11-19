package com.tripbudget.tripbudget_core.notification.services;

import com.tripbudget.tripbudget_core.notification.entities.*;
import com.tripbudget.tripbudget_core.notification.repositories.*;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {
    private final java.time.Clock clock;
    private final NotificationDeliveryRepository deliveries;
    private final NotificationRepository notifications;
    private final PushDeviceRepository devices;
    private final NotificationService service;
    private final NotificationReminderService reminders;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final UserRepository users;

    public record Claim(Long id, UUID token) {}
    public record SendContext(NotificationEntity notification, PushDeviceEntity device, String locale) {}

    @Transactional
    public List<Claim> claim() {
        Instant now = clock.instant();
        return deliveries.ready(now).stream().map(d -> {
            d.claim(now);
            return new Claim(d.getId(), d.getClaimToken());
        }).toList();
    }
    @Transactional(readOnly = true)
    public SendContext prepare(Claim claim) {
        NotificationDeliveryEntity delivery = deliveries.findById(claim.id()).orElse(null);
        if (delivery == null || !delivery.owns(claim.token())) return null;
        NotificationEntity notification = notifications.findById(delivery.getNotificationId()).orElse(null);
        PushDeviceEntity device = devices.findById(delivery.getDeviceId()).orElse(null);
        if (notification == null || notification.isDel() || device == null || !device.isEnabled() ||
            device.getGeneration() != delivery.getDeviceGeneration() ||
            !device.getUserId().equals(notification.getRecipientId())) return null;
        Instant now = clock.instant();
        if (notification.getExpiresAt() != null && !now.isBefore(notification.getExpiresAt())) return null;
        if (!service.canReceive(notification.getRecipientId(), notification.getTripId(), notification.getType())) return null;
        NotificationPreferenceEntity preference = service.preference(notification.getRecipientId());
        if (!preference.isPushEnabled()) return null;
        if (!reminders.isCurrent(notification, preference, now)) return null;
        String raw = redis.opsForValue().get("auth:session:" + device.getSessionId());
        if (raw == null) return null;
        var session = mapper.readTree(raw);
        if (!device.getUserId().toString().equals(session.path("userId").asString()) ||
            !now.isBefore(Instant.parse(session.path("expiresAt").asString())) ||
            !users.hasAcceptedPolicies(device.getUserId())) return null;
        return new SendContext(notification, device, preference.getLocale());
    }
    @Transactional
    public void acknowledge(Claim claim, String providerId, String error, boolean retry, boolean invalidDevice, long retryAfterSeconds) {
        NotificationDeliveryEntity delivery = deliveries.lockById(claim.id()).orElse(null);
        if (delivery == null || !delivery.owns(claim.token())) return;
        Instant now = clock.instant();
        if (invalidDevice) {
            devices.lockById(delivery.getDeviceId()).ifPresent(device -> {
                if (device.getGeneration() == delivery.getDeviceGeneration()) device.revoke();
            });
        }
        String status = providerId != null ? "ACCEPTED_BY_PROVIDER" : error == null ? "CANCELLED" : "FAILED";
        Instant next = now;
        if (retry && delivery.getAttempts() < 6) {
            status = "PENDING";
            long backoff = Math.min(3600, 30L << Math.min(delivery.getAttempts(), 6));
            next = now.plusSeconds(Math.max(backoff, Math.min(86400, retryAfterSeconds)) + java.util.concurrent.ThreadLocalRandom.current().nextLong(15));
        }
        delivery.complete(status, providerId, error, next);
    }
}
