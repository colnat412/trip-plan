package com.tripbudget.tripbudget_core.plantemplate.controllers;

import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateDetailResponse;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateListResponse;
import com.tripbudget.tripbudget_core.plantemplate.services.PlanTemplateQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/plan-templates")
@RequiredArgsConstructor
public class PublicPlanTemplateController {
    private final PlanTemplateQueryService queryService;
    private final HashidsService hashidsService;

    @GetMapping
    public ResponseEntity<ApiResponse<PlanTemplateListResponse>> listTemplates(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Integer duration,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        PlanTemplateListResponse response = queryService.listPublicTemplates(search, duration, tag, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Templates", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlanTemplateDetailResponse>> getTemplate(@PathVariable String id) {
        Long templateId = hashidsService.decode(id);
        PlanTemplateDetailResponse response = queryService.getPublicTemplate(templateId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Template", response));
    }
}
