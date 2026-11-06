package com.tripbudget.tripbudget_core.ai.controllers;

import com.tripbudget.tripbudget_core.ai.dtos.response.ScannedReceiptResponse;
import com.tripbudget.tripbudget_core.ai.services.AiService;
import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.CurrentUserDto;
import com.tripbudget.tripbudget_core.plan.dtos.request.AiGeneratePlanRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiService ai;

    @PostMapping("/generate-plan")
    public JsonNode generatePlan(@CurrentUser CurrentUserDto user, @RequestBody AiGeneratePlanRequest request) {
        Long currentUserId = user.id();
        return ai.generatePlan(currentUserId, request);
    }

    @PostMapping(value = "/scan-receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScannedReceiptResponse scanReceipt(@CurrentUser CurrentUserDto user, @RequestParam("file") MultipartFile file) {
        Long currentUserId = user.id();
        return ai.scanReceipt(currentUserId, file);
    }

    @GetMapping("/models")
    public Object models(@CurrentUser CurrentUserDto user) {
        Long currentUserId = user.id();
        return ai.listModels(currentUserId);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "healthy", "service", "tripbudget-ai");
    }

    @GetMapping("/test")
    public Map<String, String> test() {
        return Map.of("message", "API is working!");
    }
}
