package com.tripbudget.tripbudget_core.uploads.controllers;

import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import com.tripbudget.tripbudget_core.common.dtos.CurrentUserDto;
import com.tripbudget.tripbudget_core.uploads.dtos.response.UploadResponse;
import com.tripbudget.tripbudget_core.uploads.enums.UploadPurpose;
import com.tripbudget.tripbudget_core.uploads.services.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class UploadController {
    private final UploadService uploads;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadResponse>> upload(
            @CurrentUser CurrentUserDto user,
            @RequestParam("file") MultipartFile file,
            @RequestParam("purpose") UploadPurpose purpose,
            HttpServletRequest request
    ) throws Exception {
        Long currentUserId = user.id();
        var parts = request.getParts();
        if (parts.size() != 2 || parts.stream().filter(part -> "file".equals(part.getName())).count() != 1
                || parts.stream().filter(part -> "purpose".equals(part.getName())).count() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unexpected fields");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED, "Success", uploads.uploadImage(currentUserId, file, purpose)));
    }
}
