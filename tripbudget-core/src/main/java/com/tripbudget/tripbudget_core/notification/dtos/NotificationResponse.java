package com.tripbudget.tripbudget_core.notification.dtos;

import com.tripbudget.tripbudget_core.notification.enums.NotificationType;
import java.time.Instant;

public record NotificationResponse(String id, NotificationType type, String tripId,
        String tripName, String actorName, Instant createdAt, Instant readAt) {}
