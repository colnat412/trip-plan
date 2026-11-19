package com.tripbudget.tripbudget_core.notification.workers;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.tripbudget.tripbudget_core.notification.gateways.FirebasePushGateway;
import com.tripbudget.tripbudget_core.notification.services.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class NotificationWorker {
    private final NotificationDeliveryService deliveries;
    private final NotificationReminderService reminders;
    private final FirebasePushGateway gateway;
    private final ExecutorService executor;
    private final boolean enabled;
    private final boolean pushEnabled;
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean queued = new AtomicBoolean();
    public NotificationWorker(NotificationDeliveryService deliveries, NotificationReminderService reminders,
            FirebasePushGateway gateway, @Qualifier("notificationExecutor") ExecutorService executor,
            @Value("${notifications.enabled:false}") boolean enabled,
            @Value("${notifications.push-enabled:false}") boolean pushEnabled) {
        this.deliveries = deliveries;
        this.reminders = reminders;
        this.gateway = gateway;
        this.executor = executor;
        this.enabled = enabled;
        this.pushEnabled = pushEnabled;
    }
    @TransactionalEventListener
    public void afterCommit(NotificationService.WorkAvailable event) { wake(); }

    @Scheduled(fixedDelayString = "${notifications.recovery-delay-ms:15000}", initialDelayString = "${notifications.initial-delay-ms:1000}")
    public void recover() { wake(); }

    @Scheduled(fixedDelayString = "${notifications.reminder-delay-ms:1000}", initialDelayString = "${notifications.initial-delay-ms:1000}")
    public void reminderTick() {
        if (!enabled) return;
        try { reminders.generateDue(); }
        catch (RuntimeException exception) { log.warn("Notification reminder scan failed ({})", exception.getClass().getSimpleName()); }
    }
    private void wake() {
        if (!enabled || !pushEnabled) return;
        dirty.set(true);
        if (!queued.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try {
                    do { dirty.set(false); drain(); } while (dirty.get());
                }
                catch (RuntimeException exception) { log.warn("Notification delivery scan failed ({})", exception.getClass().getSimpleName()); }
                finally { queued.set(false); if (dirty.get()) wake(); }
            });
        } catch (RejectedExecutionException exception) {
            queued.set(false); // Durable pending rows are recovered on the next scan.
        }
    }
    private long retryAfter(FirebaseMessagingException exception) {
        if (exception.getHttpResponse() == null) return 0;
        Object header = exception.getHttpResponse().getHeaders().entrySet().stream()
            .filter(e -> "retry-after".equalsIgnoreCase(e.getKey())).map(java.util.Map.Entry::getValue)
            .findFirst().orElse(null);
        if (header instanceof java.util.List<?> values) header = values.isEmpty() ? null : values.getFirst();
        if (header == null) return 0;
        try { return Math.max(0, Long.parseLong(header.toString())); }
        catch (NumberFormatException ignored) {
            try {
                var target = java.time.ZonedDateTime.parse(header.toString(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return Math.max(0, java.time.Duration.between(java.time.Instant.now(), target).getSeconds());
            } catch (RuntimeException invalidHeader) { return 0; }
        }
    }

    private void drain() {
        for (int batch = 0; batch < 200; batch++) {
            var claims = deliveries.claim();
            if (claims.isEmpty()) return;
            for (var claim : claims) {
                try {
                    var context = deliveries.prepare(claim);
                    if (context == null) {
                        deliveries.acknowledge(claim, null, null, false, false, 0);
                        continue;
                    }
                    String providerId = gateway.send(context.notification(), context.device(), context.locale());
                    deliveries.acknowledge(claim, providerId, null, false, false, 0);
                } catch (FirebaseMessagingException exception) {
                    MessagingErrorCode code = exception.getMessagingErrorCode();
                    boolean retry = code == MessagingErrorCode.UNAVAILABLE || code == MessagingErrorCode.INTERNAL || code == MessagingErrorCode.QUOTA_EXCEEDED;
                    deliveries.acknowledge(claim, null, code == null ? "PROVIDER_ERROR" : code.name(), retry,
                        code == MessagingErrorCode.UNREGISTERED, retryAfter(exception));
                } catch (Exception exception) {
                    // No raw provider payload, addresses or credentials in logs/database.
                    deliveries.acknowledge(claim, null, "SEND_OR_PREPARE_FAILED", true, false, 0);
                }
            }
        }
        dirty.set(true); // Continue draining a backlog without waiting for the recovery timer.
    }
}
