package com.tripbudget.tripbudget_core.auth.clients;

import com.tripbudget.tripbudget_core.auth.config.AuthSettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.Map;

@Component
public class GoogleCredentialClient {
    private final NimbusJwtDecoder decoder;
    private final RestClient restClient;

    public GoogleCredentialClient(AuthSettings settings) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        restClient = RestClient.builder().requestFactory(factory).build();
        decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
        configureDecoder(decoder, settings.getGoogleClientId());
    }

    public GoogleProfile verify(String credential) {
        try {
            if (credential.split("\\.", -1).length == 3) {
                var jwt = decoder.decode(credential);
                return new GoogleProfile(jwt.getClaimAsString("email"), jwt.getClaimAsString("name"),
                        jwt.getClaimAsString("picture"));
            }
            JsonNode profile = restClient.get().uri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .headers(headers -> headers.setBearerAuth(credential))
                    .retrieve().body(JsonNode.class);
            if (profile == null) throw new IllegalStateException("Missing Google profile");
            return new GoogleProfile(text(profile, "email"), text(profile, "name"), text(profile, "picture"));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to login with Google");
        }
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asString() : null;
    }

    static void configureDecoder(NimbusJwtDecoder decoder, String clientId) {
        // Google also permits "accounts.google.com", which is not a URL as Spring's default issuer converter expects.
        decoder.setClaimSetConverter(MappedJwtClaimSetConverter.withDefaults(Map.of("iss", value -> value)));
        decoder.setJwtValidator(jwt -> validateClaims(jwt, clientId));
    }

    // Match google-auth-library's five-minute skew and maximum future expiry.
    static OAuth2TokenValidatorResult validateClaims(Jwt jwt, String clientId) {
        Instant now = Instant.now();
        String issuer = jwt.getClaimAsString("iss");
        boolean issuerValid = issuer != null && Set.of("https://accounts.google.com", "accounts.google.com")
                .contains(issuer);
        boolean audienceValid = jwt.getAudience().contains(clientId);
        boolean timeValid = jwt.getIssuedAt() != null && jwt.getExpiresAt() != null
                && !now.isBefore(jwt.getIssuedAt().minusSeconds(300))
                && !now.isAfter(jwt.getExpiresAt().plusSeconds(300))
                && jwt.getExpiresAt().isBefore(now.plusSeconds(86400));
        return issuerValid && audienceValid && timeValid ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }
}
