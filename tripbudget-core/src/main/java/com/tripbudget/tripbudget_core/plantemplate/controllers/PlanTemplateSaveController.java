package com.tripbudget.tripbudget_core.plantemplate.controllers;

import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import com.tripbudget.tripbudget_core.common.dtos.CurrentUserDto;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateListResponse;
import com.tripbudget.tripbudget_core.plantemplate.services.PlanTemplateSaveService;
import com.tripbudget.tripbudget_core.plantemplate.services.PlanTemplateQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PlanTemplateSaveController {
    private final PlanTemplateSaveService saveService;
    private final PlanTemplateQueryService queryService;
    private final HashidsService hashids;

    @PutMapping("/plan-templates/{id}/save")
    public ResponseEntity<ApiResponse<Void>> save(@PathVariable String id, @CurrentUser CurrentUserDto user) {
        Long currentUserId = user.id();
        saveService.save(currentUserId, hashids.decode(id));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Saved", null));
    }

    @DeleteMapping("/plan-templates/{id}/save")
    public ResponseEntity<ApiResponse<Void>> unsave(@PathVariable String id, @CurrentUser CurrentUserDto user) {
        Long currentUserId = user.id();
        saveService.unsave(currentUserId, hashids.decode(id));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Unsaved", null));
    }

    @GetMapping("/me/plan-template-saves/status")
    public ResponseEntity<ApiResponse<List<String>>> status(
            @CurrentUser CurrentUserDto user, @RequestParam List<String> ids) {
        Long currentUserId = user.id();
        if (ids.size() > 24) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TOO_MANY_TEMPLATE_IDS");
        List<Long> templateIds = ids.stream().map(hashids::decode).toList();
        List<String> savedIds = saveService.savedIds(currentUserId, templateIds).stream().map(hashids::encode).toList();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Saved template IDs", savedIds));
    }

    @GetMapping("/me/plan-template-saves")
    public ResponseEntity<ApiResponse<PlanTemplateListResponse>> list(
            @CurrentUser CurrentUserDto user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Long currentUserId = user.id();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Saved templates",
                queryService.listSavedTemplates(currentUserId, page, size)));
    }
}
