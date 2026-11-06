package com.tripbudget.tripbudget_core.plan.clients;

import com.tripbudget.tripbudget_core.ai.services.AiService;
import com.tripbudget.tripbudget_core.plan.dtos.request.AiGeneratePlanRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class AiPlanClient {
    private final AiService ai;

    public JsonNode generatePlan(Long currentUserId, AiGeneratePlanRequest request) {
        JsonNode response = ai.generatePlan(currentUserId, request);
        if (response == null || !response.has("days")) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid data returned from AI service");
        }
        return response;
    }
}
