package com.tripbudget.tripbudget_core.tripnote.dtos.response;

import java.time.Instant;

public record TripNoteResponse(String id, String tripId, String authorId, String authorName,
                               String authorAvatarUrl, String content, String imageUrl, long version,
                               Instant createdAt, Instant updatedAt, boolean canEdit, boolean canDelete) {}
