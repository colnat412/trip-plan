package com.tripbudget.tripbudget_core.tripnote.controllers;

import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.*;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.trip.dtos.response.PageResponse;
import com.tripbudget.tripbudget_core.tripnote.dtos.request.*;
import com.tripbudget.tripbudget_core.tripnote.dtos.response.*;
import com.tripbudget.tripbudget_core.tripnote.services.TripNoteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trip/{tripId}/notes")
@RequiredArgsConstructor
@Validated
public class TripNoteController {
    private final TripNoteService notes;
    private final HashidsService hashids;

    @GetMapping
    public ApiResponse<PageResponse<TripNoteResponse>> list(@CurrentUser CurrentUserDto user,
            @PathVariable String tripId, @RequestParam(defaultValue = "0") @Min(0) @Max(5000) int page) {
        Long currentUserId = user.id();
        return ApiResponse.success(HttpStatus.OK, "Trip notes retrieved", notes.list(currentUserId, hashids.decode(tripId), page));
    }

    @GetMapping("/{noteId}/history")
    public ApiResponse<PageResponse<TripNoteRevisionResponse>> history(@CurrentUser CurrentUserDto user,
            @PathVariable String tripId, @PathVariable String noteId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(5000) int page) {
        Long currentUserId = user.id();
        return ApiResponse.success(HttpStatus.OK, "Trip note history retrieved",
                notes.history(currentUserId, hashids.decode(tripId), hashids.decode(noteId), page));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TripNoteResponse> create(@CurrentUser CurrentUserDto user,
            @PathVariable String tripId, @Valid @RequestBody CreateTripNoteRequest request) {
        Long currentUserId = user.id();
        return ApiResponse.success(HttpStatus.CREATED, "Trip note created", notes.create(currentUserId, hashids.decode(tripId), request));
    }

    @PutMapping("/{noteId}")
    public ApiResponse<TripNoteResponse> update(@CurrentUser CurrentUserDto user, @PathVariable String tripId,
            @PathVariable String noteId, @Valid @RequestBody UpdateTripNoteRequest request) {
        Long currentUserId = user.id();
        return ApiResponse.success(HttpStatus.OK, "Trip note updated", notes.update(currentUserId, hashids.decode(tripId), hashids.decode(noteId), request));
    }

    @DeleteMapping("/{noteId}")
    public ApiResponse<Void> delete(@CurrentUser CurrentUserDto user, @PathVariable String tripId, @PathVariable String noteId) {
        Long currentUserId = user.id();
        notes.delete(currentUserId, hashids.decode(tripId), hashids.decode(noteId));
        return ApiResponse.success(HttpStatus.OK, "Trip note deleted", null);
    }
}
