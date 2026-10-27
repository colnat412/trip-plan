package com.tripbudget.tripbudget_core.plan.services;

import com.tripbudget.tripbudget_core.trip.services.TripPermissionService;
import com.tripbudget.tripbudget_core.plan.clients.AiPlanClient;

import com.tripbudget.tripbudget_core.plan.dtos.request.AiGeneratePlanRequest;
import com.tripbudget.tripbudget_core.plan.entities.PlanDayEntity;
import com.tripbudget.tripbudget_core.plan.entities.PlanActivityEntity;
import com.tripbudget.tripbudget_core.plan.enums.ActivityCategory;
import com.tripbudget.tripbudget_core.plan.repositories.PlanDayRepository;
import com.tripbudget.tripbudget_core.plan.repositories.PlanActivityRepository;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.entities.TripMemberEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;
import com.tripbudget.tripbudget_core.trip.repositories.TripMemberRepository;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import com.tripbudget.tripbudget_core.trip.events.PublicTripChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiPlanService {

    private final TripPermissionService permissions;

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final PlanDayRepository planDayRepository;
    private final PlanActivityRepository planActivityRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final AiPlanClient aiPlanClient;

    @Transactional
    public void generateAndSavePlan(Long currentUserId, Long tripId, AiGeneratePlanRequest request) {
        TripEntity trip = tripRepository.findActiveTrip(tripId);
        if (trip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }

        TripMemberEntity member = tripMemberRepository.findByTrip_IdAndUserIdAndIsDelFalse(tripId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this trip"));

        if (member.getStatus() != TripMemberStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have active access to this trip");
        }

        permissions.requireManager(tripId, currentUserId);

        if (request.getDestination() == null || request.getDestination().isBlank()) {
            request.setDestination(trip.getDestination());
        }
        
        JsonNode responseJson = aiPlanClient.generatePlan(request);

        for (JsonNode dayNode : responseJson.get("days")) {
            int dayNumber = dayNode.get("dayNumber").asInt();
            LocalDate planDate = trip.getStartDate() != null ? trip.getStartDate().plusDays(dayNumber - 1) : null;
            
            PlanDayEntity planDay = planDayRepository.findByTripIdAndDayNumberAndIsDelFalse(tripId, dayNumber)
                    .orElseGet(() -> planDayRepository.save(
                            PlanDayEntity.create(tripId, dayNumber, planDate, "Ngày " + dayNumber, null)
                    ));

            List<PlanActivityEntity> existing = planActivityRepository.findAllByDay_IdAndIsDelFalseOrderByOrderIndexAscStartTimeAsc(planDay.getId());
            int orderIndex = existing.size();

            if (dayNode.has("activities") && dayNode.get("activities").isArray()) {
                for (JsonNode actNode : dayNode.get("activities")) {
                    String title = actNode.has("title") ? actNode.get("title").asText() : "Hoạt động";
                    ActivityCategory category = parseCategory(actNode);
                    LocalTime startTime = parseOptionalTime(actNode, "startTime");
                    LocalTime endTime = parseOptionalTime(actNode, "endTime");

                    String location = actNode.has("location") ? actNode.get("location").asText() : null;
                    String note = actNode.has("note") ? actNode.get("note").asText() : null;
                    BigDecimal cost = actNode.has("estimatedCost") ? BigDecimal.valueOf(actNode.get("estimatedCost").asDouble()) : BigDecimal.ZERO;

                    PlanActivityEntity act = PlanActivityEntity.create(
                            currentUserId,
                            planDay, tripId, title, startTime, endTime, location, category, cost, orderIndex++, note
                    );
                    planActivityRepository.save(act);
                }
            }
        }

        eventPublisher.publishEvent(PublicTripChangedEvent.of(tripId));
    }

    private ActivityCategory parseCategory(JsonNode activity) {
        String category = activity.has("category") ? activity.get("category").asText() : "OTHER";
        try {
            return ActivityCategory.valueOf(category);
        } catch (IllegalArgumentException exception) {
            return ActivityCategory.OTHER;
        }
    }

    private LocalTime parseOptionalTime(JsonNode activity, String fieldName) {
        if (!activity.has(fieldName) || activity.get(fieldName).asText().isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(activity.get(fieldName).asText());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

}
