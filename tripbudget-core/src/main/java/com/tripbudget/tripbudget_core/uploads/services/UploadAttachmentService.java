package com.tripbudget.tripbudget_core.uploads.services;

import com.tripbudget.tripbudget_core.uploads.enums.UploadPurpose;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateRepository;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import com.tripbudget.tripbudget_core.uploads.repositories.UploadRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class UploadAttachmentService {
    private final UploadRepository uploads;
    private final PlanTemplateRepository templates;
    private final TripRepository trips;

    public UploadAttachmentService(UploadRepository uploads,
            PlanTemplateRepository templates,
            TripRepository trips) {
        this.uploads = uploads;
        this.templates = templates;
        this.trips = trips;
    }

    public String attachPending(Long currentUserId, UUID uploadId, UploadPurpose purpose) {
        return uploads.claimPending(currentUserId, uploadId, purpose)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Upload is unavailable"));
    }

    public void scheduleDeletion(Long currentUserId, UUID uploadId, UploadPurpose purpose) {
        if (coverIsReferenced(uploadId, purpose)) return;
        uploads.markDeletePending(currentUserId, uploadId, purpose);
    }

    public void releaseAttached(UUID uploadId, UploadPurpose purpose) {
        if (coverIsReferenced(uploadId, purpose)) return;
        uploads.releaseAttached(uploadId, purpose);
    }
    private boolean coverIsReferenced(UUID uploadId, UploadPurpose purpose) {
        return purpose == UploadPurpose.TRIP_COVER
                && (trips.existsByCoverUploadId(uploadId) || templates.existsByCoverUploadId(uploadId));
    }
}
