package com.tripbudget.tripbudget_core.uploads.repositories;

import java.util.UUID;

public record UploadAsset(
        UUID id, Long userId, String purpose, String publicId, String url,
        String resourceType, String format, Integer bytes, Integer width, Integer height, String status
) {}
