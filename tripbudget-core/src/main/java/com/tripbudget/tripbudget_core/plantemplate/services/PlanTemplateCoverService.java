package com.tripbudget.tripbudget_core.plantemplate.services;

import com.tripbudget.tripbudget_core.plantemplate.dtos.PublishTemplateRequest;
import com.tripbudget.tripbudget_core.plantemplate.entities.PlanTemplateEntity;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.uploads.enums.UploadPurpose;
import com.tripbudget.tripbudget_core.uploads.repositories.UploadRepository;
import com.tripbudget.tripbudget_core.uploads.services.UploadAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PlanTemplateCoverService {
    private final UploadRepository uploads;
    private final UploadAttachmentService attachments;

    public record Cover(UUID uploadId, String url) {}

    public Cover current(TripEntity source, PlanTemplateEntity existing) {
        return existing == null ? new Cover(source.getCoverUploadId(), source.getCoverUrl())
                : new Cover(existing.getCoverUploadId(), existing.getCoverUrl());
    }

    public Cover resolve(Long userId, TripEntity source, PlanTemplateEntity existing, PublishTemplateRequest request) {
        if (request.removeCover() && request.coverUploadId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE_COVER");
        }
        if (request.removeCover()) return new Cover(null, null);
        if (request.coverUploadId() == null) return current(source, existing);
        if (existing != null && request.coverUploadId().equals(existing.getCoverUploadId())) {
            return current(source, existing);
        }
        String url = uploads.findPendingUrl(userId, request.coverUploadId(), UploadPurpose.TRIP_COVER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "UPLOAD_UNAVAILABLE"));
        return new Cover(request.coverUploadId(), url);
    }

    public void apply(Long userId, PlanTemplateEntity template, PublishTemplateRequest request, Cover cover) {
        if (request.coverUploadId() != null && !Objects.equals(cover.uploadId(), template.getCoverUploadId())) {
            String attachedUrl = attachments.attachPending(userId, cover.uploadId(), UploadPurpose.TRIP_COVER);
            if (!Objects.equals(attachedUrl, cover.url())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "UPLOAD_CHANGED_PREVIEW_AGAIN");
            }
        }
        template.updateCover(cover.uploadId(), cover.url());
    }

    public void releasePrevious(Long userId, UUID previousId, UUID currentId) {
        if (previousId != null && !previousId.equals(currentId)) {
            attachments.scheduleDeletion(userId, previousId, UploadPurpose.TRIP_COVER);
        }
    }
}
