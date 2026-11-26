package com.tripbudget.tripbudget_core.tripnote.dtos.request;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateTripNoteRequest(@Size(max = 2000) String content, UUID imageUploadId) {}
