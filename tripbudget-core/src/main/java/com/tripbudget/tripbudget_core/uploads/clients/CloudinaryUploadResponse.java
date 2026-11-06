package com.tripbudget.tripbudget_core.uploads.clients;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CloudinaryUploadResponse(
        @JsonProperty("public_id") String publicId,
        @JsonProperty("secure_url") String secureUrl,
        @JsonProperty("resource_type") String resourceType,
        String format, Integer bytes, Integer width, Integer height
) {}
