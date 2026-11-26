package com.tripbudget.tripbudget_core.tripnote.dtos.response;

import java.time.Instant;

public record TripNoteRevisionResponse(String id, String noteId, String editorId, String editorName,
                                      String editorAvatarUrl, String content, String imageUrl,
                                      long noteVersion, Instant createdAt, boolean baseline) {}
