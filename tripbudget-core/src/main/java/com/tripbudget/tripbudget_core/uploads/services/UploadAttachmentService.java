package com.tripbudget.tripbudget_core.uploads.services;

import com.tripbudget.tripbudget_core.uploads.enums.UploadPurpose;
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

    public UploadAttachmentService(UploadRepository uploads) {
        this.uploads = uploads;
    }

    public String attachPending(Long currentUserId, UUID uploadId, UploadPurpose purpose) {
        return uploads.claimPending(currentUserId, uploadId, purpose)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Upload is unavailable"));
    }

    public void scheduleDeletion(Long currentUserId, UUID uploadId, UploadPurpose purpose) {
        uploads.markDeletePending(currentUserId, uploadId, purpose);
    }
}
