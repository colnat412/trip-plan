package com.tripbudget.tripbudget_core.plantemplate.services;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.plan.entities.PlanActivityEntity;
import com.tripbudget.tripbudget_core.plan.entities.PlanChecklistEntity;
import com.tripbudget.tripbudget_core.plan.entities.PlanDayEntity;
import com.tripbudget.tripbudget_core.plan.repositories.PlanActivityRepository;
import com.tripbudget.tripbudget_core.plan.repositories.PlanChecklistRepository;
import com.tripbudget.tripbudget_core.plan.repositories.PlanDayRepository;
import com.tripbudget.tripbudget_core.plantemplate.access.PlanTemplateAccessService;
import com.tripbudget.tripbudget_core.plantemplate.dtos.CopyTemplateRequest;
import com.tripbudget.tripbudget_core.plantemplate.dtos.TemplateSnapshot;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateCopyResponse;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateCopyResult;
import com.tripbudget.tripbudget_core.plantemplate.entities.PlanTemplateCopyEntity;
import com.tripbudget.tripbudget_core.plantemplate.entities.PlanTemplateEntity;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateCopyRepository;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateLockRepository;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateRepository;
import com.tripbudget.tripbudget_core.plantemplate.snapshot.PlanTemplateFingerprint;
import com.tripbudget.tripbudget_core.plantemplate.snapshot.TemplateSnapshotValidator;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.entities.TripMemberEntity;
import com.tripbudget.tripbudget_core.trip.repositories.TripMemberRepository;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import com.tripbudget.tripbudget_core.trip.services.TripPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanTemplateCopyService {
    private final PlanTemplateRepository templateRepository;
    private final PlanTemplateCopyRepository copyRepository;
    private final PlanTemplateLockRepository lockRepository;
    private final PlanTemplateFingerprint fingerprint;
    private final PlanTemplateAccessService accessService;
    private final TemplateSnapshotValidator snapshotValidator;
    private final TripRepository tripRepository;
    private final TripMemberRepository memberRepository;
    private final PlanDayRepository dayRepository;
    private final PlanActivityRepository activityRepository;
    private final PlanChecklistRepository checklistRepository;
    private final TripPermissionService tripPermissionService;
    private final HashidsService hashidsService;

    @Transactional
    public PlanTemplateCopyResult copy(
            Long currentUserId, Long templateId, UUID idempotencyKey, CopyTemplateRequest request
    ) {
        String requestHash = fingerprint.forCopy(templateId, request);
        lockRepository.lockCopy(currentUserId, idempotencyKey);

        Optional<PlanTemplateCopyEntity> previousCopy = copyRepository
                .findByUserIdAndIdempotencyKey(currentUserId, idempotencyKey);
        if (previousCopy.isPresent()) {
            return replayCopy(currentUserId, previousCopy.get(), requestHash);
        }

        PlanTemplateEntity template = templateRepository.findByIdForCopy(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND"));
        validateCopySource(template, request.expectedContentVersion());
        LocalDate endDate = calculateEndDate(request.startDate(), template.getDurationDays());
        TripEntity newTrip = tripRepository.save(TripEntity.create(
                currentUserId, request.name(), template.getDestination(), template.getDescription(),
                request.startDate(), endDate, template.getCurrency()
        ));
        memberRepository.save(TripMemberEntity.createOwner(newTrip, currentUserId));
        copyDaysAndActivities(currentUserId, newTrip, template.getSnapshot());
        if (request.includeChecklist()) {
            copyChecklists(newTrip.getId(), template.getSnapshot());
        }

        PlanTemplateCopyEntity receipt = copyRepository.save(PlanTemplateCopyEntity.create(
                templateId, template.getContentVersion(), currentUserId, newTrip.getId(), idempotencyKey, requestHash
        ));
        return new PlanTemplateCopyResult(toResponse(receipt), false);
    }

    private PlanTemplateCopyResult replayCopy(
            Long currentUserId, PlanTemplateCopyEntity receipt, String requestHash
    ) {
        if (!receipt.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_CHANGED");
        }
        TripEntity copiedTrip = tripRepository.findActiveTrip(receipt.getNewTripId());
        if (copiedTrip == null || copiedTrip.isDel()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COPIED_TRIP_DELETED");
        }
        tripPermissionService.requireActiveMember(copiedTrip.getId(), currentUserId);
        return new PlanTemplateCopyResult(toResponse(receipt), true);
    }

    private void validateCopySource(PlanTemplateEntity template, int expectedVersion) {
        accessService.requirePublished(template);
        if (template.getContentVersion() != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TEMPLATE_VERSION_CHANGED");
        }
        snapshotValidator.validate(template.getSnapshot(), template.getCurrency());
        if (template.getDurationDays() != template.getSnapshot().days().size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "INVALID_TEMPLATE_SNAPSHOT");
        }
    }

    private LocalDate calculateEndDate(LocalDate startDate, int durationDays) {
        try {
            LocalDate endDate = startDate.plusDays(durationDays - 1L);
            if (startDate.getYear() < 1900 || endDate.getYear() > 9999) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_START_DATE");
            }
            return endDate;
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_START_DATE");
        }
    }

    private void copyDaysAndActivities(Long currentUserId, TripEntity newTrip, TemplateSnapshot snapshot) {
        for (TemplateSnapshot.Day sourceDay : snapshot.days()) {
            LocalDate planDate = newTrip.getStartDate().plusDays(sourceDay.dayNumber() - 1L);
            PlanDayEntity newDay = dayRepository.save(PlanDayEntity.create(
                    newTrip.getId(), sourceDay.dayNumber(), planDate, sourceDay.title(), sourceDay.note()
            ));
            for (TemplateSnapshot.Activity sourceActivity : sourceDay.activities()) {
                PlanActivityEntity newActivity = PlanActivityEntity.create(
                        currentUserId, newDay, newTrip.getId(), sourceActivity.title(),
                        sourceActivity.startTime(), sourceActivity.endTime(), sourceActivity.location(),
                        sourceActivity.category(), sourceActivity.estimatedCost(), sourceActivity.orderIndex(),
                        sourceActivity.note()
                );
                activityRepository.save(newActivity);
            }
        }
    }

    private void copyChecklists(Long newTripId, TemplateSnapshot snapshot) {
        for (TemplateSnapshot.Checklist sourceItem : snapshot.checklists()) {
            checklistRepository.save(PlanChecklistEntity.create(
                    newTripId, sourceItem.title(), sourceItem.category(), null
            ));
        }
    }

    private PlanTemplateCopyResponse toResponse(PlanTemplateCopyEntity receipt) {
        return new PlanTemplateCopyResponse(
                hashidsService.encode(receipt.getNewTripId()),
                hashidsService.encode(receipt.getTemplateId()), receipt.getTemplateVersion()
        );
    }
}
