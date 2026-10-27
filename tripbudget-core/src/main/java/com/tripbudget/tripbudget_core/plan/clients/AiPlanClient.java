package com.tripbudget.tripbudget_core.plan.clients;

import com.tripbudget.tripbudget_core.plan.dtos.request.AiGeneratePlanRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
public class AiPlanClient {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.service.url:http://localhost:8000}")
    private String serviceUrl;

    @Value("${ai.service.api-key:}")
    private String apiKey;

    public JsonNode generatePlan(AiGeneratePlanRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server configuration error");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-API-Key", apiKey);
        HttpEntity<AiGeneratePlanRequest> requestEntity = new HttpEntity<>(request, headers);
        JsonNode response;
        try {
            response = restTemplate.postForObject(serviceUrl + "/api/ai/generate-plan", requestEntity, JsonNode.class);
        } catch (RestClientException exception) {
            log.error("Unable to generate a plan using the AI service", exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error connecting to AI Service: ");
        }
        if (response == null || !response.has("days")) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid data returned from AI service");
        }
        return response;
    }
}
