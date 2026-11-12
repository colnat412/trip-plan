package com.tripbudget.tripbudget_core.plan.controllers;

import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import com.tripbudget.tripbudget_core.common.dtos.CurrentUserDto;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.plan.dtos.request.*;
import com.tripbudget.tripbudget_core.plan.dtos.response.PlanActivityLogResponse;
import com.tripbudget.tripbudget_core.plan.dtos.response.PlanActivityResponse;
import com.tripbudget.tripbudget_core.plan.dtos.response.PlanChecklistResponse;
import com.tripbudget.tripbudget_core.plan.dtos.response.PlanDayResponse;
import com.tripbudget.tripbudget_core.plan.dtos.response.TripPlanOverviewResponse;
import com.tripbudget.tripbudget_core.plan.services.PlanService;
import com.tripbudget.tripbudget_core.trip.dtos.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/trip/{tripId}/plan")
@RequiredArgsConstructor
@Validated
public class PlanController {

    private final PlanService planService;
    private final HashidsService hashidsService;

    @GetMapping
    public ResponseEntity<ApiResponse<TripPlanOverviewResponse>> getTripPlanOverview(
            @PathVariable("tripId") String tripIdHash,
            @RequestParam(defaultValue = "true") boolean includeActivities,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);

        TripPlanOverviewResponse response = planService.getTripPlanOverview(currentUserId, tripId, includeActivities);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Trip plan overview retrieved successfully",
                response
        ));
    }

    @PostMapping("/days")
    public ResponseEntity<ApiResponse<PlanDayResponse>> createPlanDay(
            @PathVariable("tripId") String tripIdHash,
            @Valid @RequestBody CreatePlanDayRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);

        PlanDayResponse response = planService.createPlanDay(currentUserId, tripId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED,
                        "Plan day created successfully",
                        response
                ));
    }

    @PutMapping("/days/{dayId}")
    public ResponseEntity<ApiResponse<PlanDayResponse>> updatePlanDay(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("dayId") String dayIdHash,
            @Valid @RequestBody UpdatePlanDayRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long dayId = hashidsService.decode(dayIdHash);

        PlanDayResponse response = planService.updatePlanDay(currentUserId, tripId, dayId, request);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Plan day updated successfully",
                response
        ));
    }

    @DeleteMapping("/days/{dayId}")
    public ResponseEntity<ApiResponse<Void>> deletePlanDay(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("dayId") String dayIdHash,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long dayId = hashidsService.decode(dayIdHash);

        planService.deletePlanDay(currentUserId, tripId, dayId);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Plan day deleted successfully",
                null
        ));
    }

    @DeleteMapping("/days/{dayId}/activities")
    public ResponseEntity<ApiResponse<Void>> resetDayActivities(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("dayId") String dayIdHash,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long dayId = hashidsService.decode(dayIdHash);

        planService.resetDayActivities(currentUserId, tripId, dayId);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Day activities reset successfully",
                null
        ));
    }

    @GetMapping("/days/{dayId}/activities")
    public ResponseEntity<ApiResponse<PageResponse<PlanActivityResponse>>> getDayActivities(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("dayId") String dayIdHash,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long dayId = hashidsService.decode(dayIdHash);

        PageResponse<PlanActivityResponse> response = planService.getDayActivities(
                currentUserId,
                tripId,
                dayId,
                page,
                size
        );

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Day activities retrieved successfully",
                response
        ));
    }

    @PutMapping("/days/{dayId}/activities/order")
    public ResponseEntity<ApiResponse<Void>> reorderDayActivities(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("dayId") String dayIdHash,
            @Valid @RequestBody ReorderDayActivitiesRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long dayId = hashidsService.decode(dayIdHash);
        planService.reorderDayActivities(currentUserId, tripId, dayId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Day route updated successfully", null));
    }

    @PostMapping("/days/{dayId}/activities")
    public ResponseEntity<ApiResponse<PlanActivityResponse>> createActivity(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("dayId") String dayIdHash,
            @Valid @RequestBody CreateActivityRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long dayId = hashidsService.decode(dayIdHash);

        PlanActivityResponse response = planService.createActivity(currentUserId, tripId, dayId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED,
                        "Activity created successfully",
                        response
                ));
    }

    @PutMapping("/activities/{activityId}")
    public ResponseEntity<ApiResponse<PlanActivityResponse>> updateActivity(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("activityId") String activityIdHash,
            @Valid @RequestBody UpdateActivityRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long activityId = hashidsService.decode(activityIdHash);

        PlanActivityResponse response = planService.updateActivity(currentUserId, tripId, activityId, request);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Activity updated successfully",
                response
        ));
    }

    @PatchMapping("/activities/{activityId}/image")
    public ResponseEntity<ApiResponse<PlanActivityResponse>> updateActivityImage(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("activityId") String activityIdHash,
            @Valid @RequestBody UpdateActivityImageRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long activityId = hashidsService.decode(activityIdHash);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Activity image updated successfully",
                planService.updateActivityImage(currentUserId, tripId, activityId, request)));
    }

    @PatchMapping("/activities/{activityId}/status")
    public ResponseEntity<ApiResponse<PlanActivityResponse>> updateActivityStatus(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("activityId") String activityIdHash,
            @Valid @RequestBody UpdateActivityStatusRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long activityId = hashidsService.decode(activityIdHash);

        PlanActivityResponse response = planService.updateActivityStatus(currentUserId, tripId, activityId, request.status());

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Activity status updated successfully",
                response
        ));
    }

    @DeleteMapping("/activities/{activityId}")
    public ResponseEntity<ApiResponse<Void>> deleteActivity(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("activityId") String activityIdHash,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long activityId = hashidsService.decode(activityIdHash);

        planService.deleteActivity(currentUserId, tripId, activityId);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Activity deleted successfully",
                null
        ));
    }

    @GetMapping("/checklists")
    public ResponseEntity<ApiResponse<List<PlanChecklistResponse>>> getChecklists(
            @PathVariable("tripId") String tripIdHash,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);

        List<PlanChecklistResponse> response = planService.getChecklists(currentUserId, tripId);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Checklists retrieved successfully",
                response
        ));
    }

    @PostMapping("/checklists")
    public ResponseEntity<ApiResponse<PlanChecklistResponse>> createChecklist(
            @PathVariable("tripId") String tripIdHash,
            @Valid @RequestBody CreateChecklistRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);

        PlanChecklistResponse response = planService.createChecklist(currentUserId, tripId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED,
                        "Checklist item created successfully",
                        response
                ));
    }

    @PutMapping("/checklists/{checklistId}")
    public ResponseEntity<ApiResponse<PlanChecklistResponse>> updateChecklist(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("checklistId") String checklistIdHash,
            @Valid @RequestBody UpdateChecklistRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long checklistId = hashidsService.decode(checklistIdHash);

        PlanChecklistResponse response = planService.updateChecklist(currentUserId, tripId, checklistId, request);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Checklist item updated successfully",
                response
        ));
    }

    @PatchMapping("/checklists/{checklistId}/toggle")
    public ResponseEntity<ApiResponse<PlanChecklistResponse>> toggleChecklist(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("checklistId") String checklistIdHash,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long checklistId = hashidsService.decode(checklistIdHash);

        PlanChecklistResponse response = planService.toggleChecklist(currentUserId, tripId, checklistId);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Checklist item status toggled successfully",
                response
        ));
    }

    @DeleteMapping("/checklists/{checklistId}")
    public ResponseEntity<ApiResponse<Void>> deleteChecklist(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("checklistId") String checklistIdHash,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long checklistId = hashidsService.decode(checklistIdHash);

        planService.deleteChecklist(currentUserId, tripId, checklistId);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Checklist item deleted successfully",
                null
        ));
    }

    @GetMapping("/activities/logs")
    public ResponseEntity<ApiResponse<PageResponse<PlanActivityLogResponse>>> getActivityLogs(
            @PathVariable("tripId") String tripIdHash,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);

        PageResponse<PlanActivityLogResponse> response = planService.getActivityLogs(
                currentUserId,
                tripId,
                page,
                size
        );

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Activity logs retrieved successfully",
                response
        ));
    }
}

