package com.tripbudget.tripbudget_core.plan.dtos.request;

import java.util.UUID;

public record UpdateActivityImageRequest(UUID imageUploadId, Boolean removeImage) {}
