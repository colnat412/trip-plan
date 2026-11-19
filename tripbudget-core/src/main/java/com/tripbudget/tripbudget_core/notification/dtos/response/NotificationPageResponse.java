package com.tripbudget.tripbudget_core.notification.dtos.response;

import java.time.Instant;
import java.util.List;
public record NotificationPageResponse(List<NotificationResponse> items, String nextCursor, Instant through) {}
