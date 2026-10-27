package com.tripbudget.tripbudget_core.plantemplate.access;

import com.tripbudget.tripbudget_core.plantemplate.entities.PlanTemplateEntity;
import com.tripbudget.tripbudget_core.plantemplate.enums.PlanTemplateStatus;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import com.tripbudget.tripbudget_core.trip.services.TripPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class PlanTemplateAccessService {
    private final TripRepository tripRepository;
    private final TripPermissionService tripPermissionService;

    public TripEntity requireSourceOwner(Long currentUserId, Long sourceTripId) {
        TripEntity sourceTrip = tripRepository.findActiveTrip(sourceTripId);
        if (sourceTrip == null || sourceTrip.isDel()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND");
        }
        if (!sourceTrip.getOwnerId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OWNER_REQUIRED");
        }
        tripPermissionService.requireActiveMember(sourceTripId, currentUserId);
        return sourceTrip;
    }

    public void requireAuthor(Long currentUserId, PlanTemplateEntity template) {
        if (!template.getAuthorId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OWNER_REQUIRED");
        }
    }

    public void requirePublished(PlanTemplateEntity template) {
        TripEntity sourceTrip = tripRepository.findActiveTrip(template.getSourceTripId());
        if (template.getStatus() != PlanTemplateStatus.PUBLISHED || sourceTrip == null || sourceTrip.isDel()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND");
        }
    }
}
