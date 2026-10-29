package com.tripbudget.tripbudget_core.plantemplate.snapshot;

import com.tripbudget.tripbudget_core.plantemplate.dtos.CopyTemplateRequest;
import com.tripbudget.tripbudget_core.plantemplate.dtos.PublishTemplateRequest;
import com.tripbudget.tripbudget_core.plantemplate.dtos.TemplateSnapshot;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PlanTemplateFingerprint {
    private final ObjectMapper objectMapper;

    public String forPublication(TripEntity sourceTrip, PublishTemplateRequest request, TemplateSnapshot snapshot, java.util.UUID coverId, String coverUrl) {
        return hash(Arrays.asList(
                sourceTrip.getId(), sourceTrip.getStartDate(), sourceTrip.getEndDate(),
                sourceTrip.getBaseCurrency(), request.title().trim(), request.destination().trim(),
                request.description(), request.tags(), snapshot, coverId, coverUrl,
                request.coverUploadId(), request.removeCover()
        ));
    }

    public String forCopy(Long templateId, CopyTemplateRequest request) {
        return hash(List.of(
                templateId, request.name().trim(), request.startDate(),
                request.includeChecklist(), request.expectedContentVersion()
        ));
    }

    private String hash(Object value) {
        try {
            String serializedValue = objectMapper.writeValueAsString(value);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(serializedValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
