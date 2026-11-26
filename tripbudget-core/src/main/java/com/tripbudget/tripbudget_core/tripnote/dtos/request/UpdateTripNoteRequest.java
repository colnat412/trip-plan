package com.tripbudget.tripbudget_core.tripnote.dtos.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record UpdateTripNoteRequest(@Size(max = 2000) String content, @NotNull @PositiveOrZero Long version,
                                   UUID imageUploadId, boolean removeImage) {}
