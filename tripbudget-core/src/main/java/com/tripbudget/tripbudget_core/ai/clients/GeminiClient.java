package com.tripbudget.tripbudget_core.ai.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class GeminiClient {
    private final RestClient client;
    private final RestClient receiptClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GeminiClient(@Value("${GEMINI_API_KEY:}") String apiKey,
                        @Value("${GEMINI_API_BASE_URL:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
                        RestClient.Builder builder, ObjectMapper objectMapper) {
        this.apiKey = apiKey.trim();
        this.objectMapper = objectMapper;
        client = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory(0)).build();
        receiptClient = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory(25)).build();
    }

    public ProviderResponse generate(String model, Map<String, Object> payload, boolean receipt) {
        requireConfigured();
        try {
            return (receipt ? receiptClient : client).post().uri("/" + model + ":generateContent")
                    .header("x-goog-api-key", apiKey).body(payload)
                    .exchange((request, response) -> new ProviderResponse(response.getStatusCode().value(),
                            objectMapper.readTree(response.getBody())));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to connect to Gemini");
        }
    }

    public ProviderResponse listModels() {
        requireConfigured();
        try {
            return client.get().uri("/models").header("x-goog-api-key", apiKey)
                    .exchange((request, response) -> new ProviderResponse(response.getStatusCode().value(),
                            objectMapper.readTree(response.getBody())));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to connect to Gemini");
        }
    }

    private JdkClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
        if (timeoutSeconds > 0) factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return factory;
    }

    public void requireConfigured() {
        if (apiKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing GEMINI_API_KEY");
        }
    }

    public record ProviderResponse(int status, JsonNode body) {}
}
