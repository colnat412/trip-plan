package com.tripbudget.tripbudget_core.trip.services;

import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.uploads.enums.UploadPurpose;
import com.tripbudget.tripbudget_core.uploads.services.UploadAttachmentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class TripCoverService {
    private final UploadAttachmentService uploads;

    public TripCoverService(UploadAttachmentService uploads) {
        this.uploads = uploads;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateCover(Long currentUserId, TripEntity trip, UUID uploadId, boolean removeCover) {
        if (uploadId != null && removeCover) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot replace and remove cover together");
        }
        if (uploadId == null && !removeCover) return;
        if (uploadId != null && uploadId.equals(trip.getCoverUploadId())) return;

        String url = uploadId == null ? null
                : uploads.attachPending(currentUserId, uploadId, UploadPurpose.TRIP_COVER);

        UUID previousUploadId = trip.getCoverUploadId();
        trip.updateCover(uploadId, url);
        if (previousUploadId != null) {
            uploads.scheduleDeletion(currentUserId, previousUploadId, UploadPurpose.TRIP_COVER);
        }
    }
}
