package com.tripbudget.tripbudget_core.notification.services;

import com.tripbudget.tripbudget_core.realtime.RealtimeSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxWorker {
    private final OutboxStore store;
    private final RealtimeSessions sessions;

    @Scheduled(fixedDelayString = "${notifications.dispatch-delay-ms:1000}")
    public void dispatch() {
        for (var item : store.due()) {
            UUID token = UUID.randomUUID();
            if (!store.claim(item.getId(), token)) continue;
            boolean success = false;
            try {
                sessions.publish(item.getRecipientId(), item.getEventId(), item.getEventType());
                success = true; // No local socket is normal; the API retains offline history.
            } catch (RuntimeException error) {
                log.warn("Notification outbox {} attempt {} failed: {}", item.getId(), item.getAttempts() + 1,
                        error.getClass().getSimpleName());
            }
            store.finish(item.getId(), token, success, item.getAttempts() + 1);
            if (!success && item.getAttempts() + 1 >= 10) log.error("Notification outbox {} moved to FAILED", item.getId());
        }
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void cleanup() { store.cleanup(); }
}
