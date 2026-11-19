package com.tripbudget.tripbudget_core.notification.dtos.response;

import java.time.Instant;
import com.tripbudget.tripbudget_core.notification.enums.NotificationAccessStatus;
public record NotificationResponse(String id, String type, String tripId, String tripName,
    String subject, String targetId, Instant createdAt, Instant readAt, String dayId, NotificationAccessStatus accessStatus, String invitationStatus) {}
