package com.tripbudget.tripbudget_core.plan.services;

import com.tripbudget.tripbudget_core.trip.services.TripPermissionService;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    @Value("${ai.service.api-key:}")
    private String aiServiceApiKey;

    @Transactional
    public void generateAndSavePlan(Long currentUserId, Long tripId, AiGeneratePlanRequest request) {
        if (aiServiceApiKey == null || aiServiceApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server configuration error");
        }

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
        
        RestTemplate restTemplate = new RestTemplate();
        String url = aiServiceUrl + "/api/ai/generate-plan";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-API-Key", aiServiceApiKey);
        HttpEntity<AiGeneratePlanRequest> httpEntity = new HttpEntity<>(request, headers);

        JsonNode responseJson;
        try {
            responseJson = restTemplate.postForObject(url, httpEntity, JsonNode.class);
        } catch (Exception e) {
            System.out.println("Error connecting to AI Service: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error connecting to AI Service: " );
        }

        if (responseJson == null || !responseJson.has("days")) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid data returned from AI service");
        }

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
                    String categoryStr = actNode.has("category") ? actNode.get("category").asText() : "OTHER";
                    ActivityCategory category;
                    try {
                        category = ActivityCategory.valueOf(categoryStr);
                    } catch (Exception e) {
                        category = ActivityCategory.OTHER;
                    }
                    
                    LocalTime startTime = null;
                    if (actNode.has("startTime") && !actNode.get("startTime").asText().isEmpty()) {
                        try { startTime = LocalTime.parse(actNode.get("startTime").asText()); } catch (Exception ignored) {}
                    }
                    
                    LocalTime endTime = null;
                    if (actNode.has("endTime") && !actNode.get("endTime").asText().isEmpty()) {
                        try { endTime = LocalTime.parse(actNode.get("endTime").asText()); } catch (Exception ignored) {}
                    }
                    
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
}
