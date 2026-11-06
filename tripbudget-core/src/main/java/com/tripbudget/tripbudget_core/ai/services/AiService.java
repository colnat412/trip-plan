package com.tripbudget.tripbudget_core.ai.services;

import com.tripbudget.tripbudget_core.ai.clients.GeminiClient;
import com.tripbudget.tripbudget_core.ai.dtos.response.ReceiptItemResponse;
import com.tripbudget.tripbudget_core.ai.dtos.response.ScannedReceiptResponse;
import com.tripbudget.tripbudget_core.plan.dtos.request.AiGeneratePlanRequest;
import com.tripbudget.tripbudget_core.user.services.UserService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AiService {
    private static final List<String> PLAN_MODELS = List.of("models/gemini-flash-lite-latest",
            "models/gemini-3.5-flash-lite", "models/gemini-3.1-flash-lite", "models/gemini-flash-latest", "models/gemini-3.8-flash");
    private static final List<String> RECEIPT_MODELS = List.of("models/gemini-2.5-flash", "models/gemini-flash-lite-latest",
            "models/gemini-3.5-flash-lite", "models/gemini-2.5-flash-lite", "models/gemini-flash-latest");
    private static final Set<String> RECEIPT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "application/pdf");
    private static final long MAX_RECEIPT_BYTES = 10 * 1024 * 1024;
    private final GeminiClient gemini;
    private final UserService users;
    private final ObjectMapper objectMapper;
    private final String planPrompt;
    private final String receiptPrompt;

    public AiService(GeminiClient gemini, ObjectMapper objectMapper, UserService users) throws IOException {
        this.gemini = gemini;
        this.users = users;
        this.objectMapper = objectMapper;
        planPrompt = new ClassPathResource("prompts/trip-plan.txt").getContentAsString(StandardCharsets.UTF_8);
        receiptPrompt = new ClassPathResource("prompts/receipt-scan.txt").getContentAsString(StandardCharsets.UTF_8);
    }

    public JsonNode generatePlan(Long currentUserId, AiGeneratePlanRequest request) {
        users.authenticatedUser(currentUserId);
        String preferences = request.getPreferences();
        Map<String, String> values = Map.of(
                "destination", request.getDestination(),
                "days", Integer.toString(request.getDays()),
                "people", Integer.toString(request.getPeople()),
                "budget", formatBudget(request.getBudget()),
                "preferences", preferences == null || preferences.isEmpty() ? "Không có" : preferences);
        String prompt = Pattern.compile("\\{\\{(destination|days|people|budget|preferences)\\}\\}")
                .matcher(planPrompt).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(values.get(match.group(1))));
        Map<String, Object> payload = payload(List.of(Map.of("text", prompt)), 0.7);
        for (String model : PLAN_MODELS) {
            var response = gemini.generate(model, payload, false);
            if (response.status() == 503) {
                waitBeforeRetry();
                response = gemini.generate(model, payload, false);
            }
            if (response.status() == 200) {
                return parseContent(response.body(), false);
            }
        }
        throw failure("Đã thử toàn bộ danh sách model Gemini nhưng đều thất bại!");
    }

    public ScannedReceiptResponse scanReceipt(Long currentUserId, MultipartFile file) {
        users.authenticatedUser(currentUserId);
        try {
            gemini.requireConfigured();
        } catch (ResponseStatusException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getReason());
        }
        String contentType = file.getContentType();
        if (contentType == null || contentType.isEmpty()) contentType = "image/jpeg";
        if (!RECEIPT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image type: " + contentType);
        }
        if (file.getSize() > MAX_RECEIPT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image size exceeds the maximum limit of 10.0 MB");
        }
        String image;
        try {
            image = Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid receipt file");
        }
        Map<String, Object> payload = payload(List.of(Map.of("text", receiptPrompt),
                Map.of("inlineData", Map.of("mimeType", contentType, "data", image))), 0.1);
        JsonNode result = null;
        for (String model : RECEIPT_MODELS) {
            try {
                var response = gemini.generate(model, payload, true);
                if (response.status() == 200 && !response.body().path("candidates").isEmpty()) {
                    result = parseContent(response.body(), true);
                    break;
                }
            } catch (RuntimeException exception) {
                // Receipt scanning tries the next model after transport or JSON parsing failures.
            }
        }
        if (result == null) throw failure("Unable to scan receipt with AI service");
        return receiptResponse(result);
    }

    public Object listModels(Long currentUserId) {
        users.authenticatedUser(currentUserId);
        var response = gemini.listModels();
        if (response.status() != 200) return Map.of("error", response.body());
        List<String> models = new ArrayList<>();
        response.body().path("models").forEach(model -> models.add(model.path("name").asString()));
        return Map.of("models", models);
    }

    private Map<String, Object> payload(List<Map<String, Object>> parts, double temperature) {
        return Map.of("contents", List.of(Map.of("parts", parts)), "generationConfig",
                Map.of("temperature", temperature, "responseMimeType", "application/json"));
    }

    private String formatBudget(double budget) {
        // Python's float format uses half-even rounding on the binary float value.
        BigDecimal rounded = new BigDecimal(budget).setScale(0, RoundingMode.HALF_EVEN);
        if (rounded.signum() == 0 && Double.doubleToRawLongBits(budget) < 0) return "-0";
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US)).format(rounded);
    }

    private JsonNode parseContent(JsonNode response, boolean removeMarkdown) {
        JsonNode content = response.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (!content.isString()) throw failure("Invalid data returned from AI service");
        String text = content.asString();
        if (removeMarkdown) text = text.strip().replace("```json", "").replace("```", "").strip();
        try {
            return objectMapper.readTree(text);
        } catch (RuntimeException exception) {
            throw failure("Invalid data returned from AI service");
        }
    }

    private ScannedReceiptResponse receiptResponse(JsonNode result) {
        try {
            List<ReceiptItemResponse> items = new ArrayList<>();
            if (result.has("items") && !result.get("items").isArray()) throw new IllegalArgumentException();
            result.path("items").forEach(item -> items.add(new ReceiptItemResponse(requiredText(item, "name"),
                    optionalNumber(item, "quantity", 1.0), optionalNumber(item, "price", 0.0))));
            String currency = result.has("currency") ? requiredText(result, "currency") : "VND";
            return new ScannedReceiptResponse(requiredText(result, "merchant_name"), requiredNumber(result, "amount"),
                    currency, nullableText(result, "expense_date"), requiredText(result, "category"),
                    optionalNumber(result, "confidence", 0.9), items, nullableText(result, "raw_text"));
        } catch (RuntimeException exception) {
            throw failure("Invalid receipt data returned from AI service");
        }
    }

    private String requiredText(JsonNode node, String field) {
        if (!node.path(field).isString()) throw new IllegalArgumentException("Missing receipt field");
        return node.get(field).asString();
    }

    private String nullableText(JsonNode node, String field) {
        return node.hasNonNull(field) ? requiredText(node, field) : null;
    }

    private double requiredNumber(JsonNode node, String field) {
        if (!node.hasNonNull(field)) throw new IllegalArgumentException("Missing receipt amount");
        double number = Double.parseDouble(node.get(field).asString());
        if (!Double.isFinite(number)) throw new IllegalArgumentException("Invalid receipt number");
        return number;
    }

    private Double optionalNumber(JsonNode node, String field, double defaultValue) {
        if (!node.has(field)) return defaultValue;
        return node.get(field).isNull() ? null : requiredNumber(node, field);
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("AI request interrupted");
        }
    }

    private ResponseStatusException failure(String message) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
