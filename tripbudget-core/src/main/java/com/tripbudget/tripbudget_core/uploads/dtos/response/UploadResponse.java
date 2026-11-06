package com.tripbudget.tripbudget_core.uploads.dtos.response;

import java.util.UUID;

public record UploadResponse(
        UUID uploadId, String publicId, String url, String resourceType, String format,
        Integer bytes, Integer width, Integer height, String purpose, String status
) {}
