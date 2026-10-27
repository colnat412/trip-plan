package com.tripbudget.tripbudget_core.plantemplate.services;

import com.tripbudget.tripbudget_core.plantemplate.access.PlanTemplateAccessService;
import com.tripbudget.tripbudget_core.plantemplate.dtos.PublishTemplateRequest;
import com.tripbudget.tripbudget_core.plantemplate.dtos.TemplateSnapshot;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateDetailResponse;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplatePreviewResponse;
import com.tripbudget.tripbudget_core.plantemplate.entities.PlanTemplateEntity;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateLockRepository;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateRepository;
import com.tripbudget.tripbudget_core.plantemplate.snapshot.PlanTemplateFingerprint;
import com.tripbudget.tripbudget_core.plantemplate.snapshot.PlanTemplateSnapshotBuilder;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlanTemplateService {
    private final PlanTemplateRepository templateRepository;
    private final PlanTemplateLockRepository lockRepository;
    private final PlanTemplateAccessService accessService;
    private final PlanTemplateSnapshotBuilder snapshotBuilder;
    private final PlanTemplateFingerprint fingerprint;
    private final PlanTemplateQueryService queryService;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PlanTemplatePreviewResponse preview(Long currentUserId, Long sourceTripId, PublishTemplateRequest request) {
        TripEntity sourceTrip = accessService.requireSourceOwner(currentUserId, sourceTripId);
        TemplateSnapshot snapshot = snapshotBuilder.build(sourceTrip, request);
        int currentVersion = templateRepository.findBySourceTripId(sourceTripId)
                .map(PlanTemplateEntity::getContentVersion).orElse(0);
        return new PlanTemplatePreviewResponse(
                snapshot, fingerprint.forPublication(sourceTrip, request, snapshot), currentVersion
        );
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public PlanTemplateDetailResponse publish(Long currentUserId, Long sourceTripId, PublishTemplateRequest request) {
        return publishSource(currentUserId, sourceTripId, null, request);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public PlanTemplateDetailResponse update(Long currentUserId, Long templateId, PublishTemplateRequest request) {
        PlanTemplateEntity template = findTemplate(templateId);
        return publishSource(currentUserId, template.getSourceTripId(), templateId, request);
    }

    @Transactional
    public void unpublish(Long currentUserId, Long templateId) {
        PlanTemplateEntity template = templateRepository.findByIdForUpdate(templateId)
                .orElseThrow(this::templateNotFound);
        accessService.requireAuthor(currentUserId, template);
        template.unpublish();
    }

    private PlanTemplateDetailResponse publishSource(
            Long currentUserId, Long sourceTripId, Long expectedTemplateId, PublishTemplateRequest request
    ) {
        lockRepository.lockPublication(sourceTripId);
        TripEntity sourceTrip = accessService.requireSourceOwner(currentUserId, sourceTripId);
        PlanTemplateEntity template = findOrCreatePublication(currentUserId, sourceTripId);
        if (expectedTemplateId != null && !expectedTemplateId.equals(template.getId())) {
            throw templateNotFound();
        }
        accessService.requireAuthor(currentUserId, template);
        if (!Objects.equals(request.expectedContentVersion(), template.getContentVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TEMPLATE_VERSION_CHANGED");
        }

        TemplateSnapshot snapshot = snapshotBuilder.build(sourceTrip, request);
        String currentFingerprint = fingerprint.forPublication(sourceTrip, request, snapshot);
        if (!Objects.equals(request.sourceFingerprint(), currentFingerprint)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SOURCE_CHANGED_PREVIEW_AGAIN");
        }
        template.publish(request, snapshot, sourceTrip.getBaseCurrency());
        templateRepository.saveAndFlush(template);
        return queryService.toDetailResponse(template);
    }

    private PlanTemplateEntity findOrCreatePublication(Long currentUserId, Long sourceTripId) {
        Optional<PlanTemplateEntity> existingTemplate = templateRepository.findBySourceTripId(sourceTripId);
        if (existingTemplate.isEmpty()) {
            return PlanTemplateEntity.create(currentUserId, sourceTripId);
        }
        return templateRepository.findByIdForUpdate(existingTemplate.get().getId())
                .orElseThrow(this::templateNotFound);
    }

    private PlanTemplateEntity findTemplate(Long templateId) {
        return templateRepository.findById(templateId).orElseThrow(this::templateNotFound);
    }

    private ResponseStatusException templateNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND");
    }
}
