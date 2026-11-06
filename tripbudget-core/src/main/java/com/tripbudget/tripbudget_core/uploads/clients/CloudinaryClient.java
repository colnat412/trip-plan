package com.tripbudget.tripbudget_core.uploads.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class CloudinaryClient {
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final RestClient restClient;

    public CloudinaryClient(
            @Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${CLOUDINARY_API_SECRET:}") String apiSecret,
            RestClient.Builder builder
    ) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        restClient = builder.clone().requestFactory(factory).build();
    }

    public CloudinaryUploadResponse upload(byte[] bytes, String publicId) {
        requireConfigured();
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("allowed_formats", "jpg,jpeg,png,webp");
        parameters.put("overwrite", "false");
        parameters.put("public_id", publicId);
        parameters.put("timestamp", Long.toString(Instant.now().getEpochSecond()));
        var body = signedBody(parameters);
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() { return "image"; }
        });
        try {
            CloudinaryUploadResponse result = restClient.post().uri(endpoint("upload"))
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(body)
                    .retrieve().body(CloudinaryUploadResponse.class);
            if (result == null || result.publicId() == null || result.secureUrl() == null) {
                throw new IllegalStateException("Invalid upload response");
            }
            return result;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error upload image");
        }
    }

    public void delete(String publicId) {
        requireConfigured();
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("invalidate", "true");
        parameters.put("public_id", publicId);
        parameters.put("timestamp", Long.toString(Instant.now().getEpochSecond()));
        try {
            JsonNode result = restClient.post().uri(endpoint("destroy"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(signedBody(parameters))
                    .retrieve().body(JsonNode.class);
            String status = result == null ? "" : result.path("result").asString();
            if (!"ok".equals(status) && !"not found".equals(status)) {
                throw new IllegalStateException("Invalid deletion response");
            }
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to delete Cloudinary image");
        }
    }

    private LinkedMultiValueMap<String, Object> signedBody(Map<String, String> parameters) {
        var body = new LinkedMultiValueMap<String, Object>();
        parameters.forEach(body::add);
        body.add("api_key", apiKey);
        body.add("signature", signature(parameters));
        return body;
    }

    String signature(Map<String, String> parameters) {
        String canonical = new TreeMap<>(parameters).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("&"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest((canonical + apiSecret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to sign upload");
        }
    }

    private String endpoint(String action) {
        return "https://api.cloudinary.com/v1_1/" + cloudName + "/image/" + action;
    }

    private void requireConfigured() {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error upload image");
        }
    }
}
