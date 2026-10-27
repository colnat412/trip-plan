package com.tripbudget.tripbudget_core.plantemplate.controllers;

import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import com.tripbudget.tripbudget_core.common.dtos.CurrentUserDto;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.plantemplate.dtos.CopyTemplateRequest;
import com.tripbudget.tripbudget_core.plantemplate.dtos.PublishTemplateRequest;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateCopyResponse;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateCopyResult;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateDetailResponse;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateListResponse;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplatePreviewResponse;
import com.tripbudget.tripbudget_core.plantemplate.services.PlanTemplateCopyService;
import com.tripbudget.tripbudget_core.plantemplate.services.PlanTemplateQueryService;
import com.tripbudget.tripbudget_core.plantemplate.services.PlanTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PlanTemplateController {
    private final PlanTemplateService templateService;
    private final PlanTemplateQueryService queryService;
    private final PlanTemplateCopyService copyService;
    private final HashidsService hashidsService;

    @GetMapping("/me/plan-templates")
    public ResponseEntity<ApiResponse<PlanTemplateListResponse>> listMyTemplates(
            @CurrentUser CurrentUserDto user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        Long currentUserId = user.id();
        PlanTemplateListResponse response = queryService.listMyTemplates(currentUserId, page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "My templates", response));
    }

    @PostMapping("/trip/{tripId}/plan-template/preview")
    public ResponseEntity<ApiResponse<PlanTemplatePreviewResponse>> previewTemplate(
            @PathVariable String tripId,
            @Valid @RequestBody PublishTemplateRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long sourceTripId = hashidsService.decode(tripId);
        PlanTemplatePreviewResponse response = templateService.preview(currentUserId, sourceTripId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Preview", response));
    }

    @PostMapping("/trip/{tripId}/plan-template")
    public ResponseEntity<ApiResponse<PlanTemplateDetailResponse>> publishTemplate(
            @PathVariable String tripId,
            @Valid @RequestBody PublishTemplateRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long sourceTripId = hashidsService.decode(tripId);
        PlanTemplateDetailResponse response = templateService.publish(currentUserId, sourceTripId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Published", response));
    }

    @PutMapping("/plan-templates/{id}")
    public ResponseEntity<ApiResponse<PlanTemplateDetailResponse>> updateTemplate(
            @PathVariable String id,
            @Valid @RequestBody PublishTemplateRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long templateId = hashidsService.decode(id);
        PlanTemplateDetailResponse response = templateService.update(currentUserId, templateId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Published", response));
    }

    @PostMapping("/plan-templates/{id}/unpublish")
    public ResponseEntity<ApiResponse<Void>> unpublishTemplate(
            @PathVariable String id,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long templateId = hashidsService.decode(id);
        templateService.unpublish(currentUserId, templateId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Unpublished", null));
    }

    @PostMapping("/plan-templates/{id}/copy")
    public ResponseEntity<ApiResponse<PlanTemplateCopyResponse>> copyTemplate(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CopyTemplateRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        Long templateId = hashidsService.decode(id);
        PlanTemplateCopyResult result = copyService.copy(currentUserId, templateId, idempotencyKey, request);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.success(status, "Trip created", result.response()));
    }
}
