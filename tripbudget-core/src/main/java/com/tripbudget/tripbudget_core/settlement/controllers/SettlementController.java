package com.tripbudget.tripbudget_core.settlement.controllers;

import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import com.tripbudget.tripbudget_core.common.dtos.CurrentUserDto;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.settlement.dtos.request.CreateSettlementRequest;
import com.tripbudget.tripbudget_core.settlement.dtos.request.RespondSettlementRequest;
import com.tripbudget.tripbudget_core.settlement.dtos.response.SettlementResponse;
import com.tripbudget.tripbudget_core.settlement.dtos.response.TripSettlementSummaryResponse;
import com.tripbudget.tripbudget_core.settlement.services.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trip/{tripId}/settlement")
@RequiredArgsConstructor
@Validated
public class SettlementController {

    private final SettlementService settlementService;
    private final HashidsService hashidsService;

    @GetMapping
    public ResponseEntity<ApiResponse<TripSettlementSummaryResponse>> getSettlementSummary(
            @PathVariable("tripId") String tripIdHash,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);

        TripSettlementSummaryResponse response = settlementService.getSettlementSummary(currentUserId, tripId);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Settlement summary retrieved successfully",
                response
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SettlementResponse>> createSettlement(
            @PathVariable("tripId") String tripIdHash,
            @Valid @RequestBody CreateSettlementRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);

        SettlementResponse response = settlementService.createSettlement(currentUserId, tripId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED,
                        "Settlement recorded successfully",
                        response
                ));
    }

    @DeleteMapping("/{settlementId}")
    public ResponseEntity<ApiResponse<Void>> deleteSettlement(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("settlementId") String settlementIdHash,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long tripId = hashidsService.decode(tripIdHash);
        Long settlementId = hashidsService.decode(settlementIdHash);

        settlementService.deleteSettlement(currentUserId, tripId, settlementId);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Settlement deletion requested",
                null
        ));
    }

    @PostMapping("/{settlementId}/respond")
    public ResponseEntity<ApiResponse<SettlementResponse>> respondToSettlement(
            @PathVariable("tripId") String tripIdHash,
            @PathVariable("settlementId") String settlementIdHash,
            @Valid @RequestBody RespondSettlementRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        SettlementResponse response = settlementService.respondToSettlement(currentUserId,
                hashidsService.decode(tripIdHash), hashidsService.decode(settlementIdHash), request.action());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Settlement response saved", response));
    }
}

