package com.tripbudget.tripbudget_core.security;

import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class RedisSessionValidationFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final com.tripbudget.tripbudget_core.user.repositories.UserRepository users;

    public RedisSessionValidationFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            com.tripbudget.tripbudget_core.user.repositories.UserRepository users) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String sessionId = jwtAuth.getToken().getClaimAsString("sid");
            String tokenType = jwtAuth.getToken().getClaimAsString("type");

            if (!"access".equals(tokenType) || sessionId == null) {
                reject(response, "Unauthorized");
                return;
            }

            String sessionRaw = redisTemplate.opsForValue().get("auth:session:" + sessionId);

            if (sessionRaw == null) {
                reject(response, "Unauthorized");
                return;
            }
            Instant expiresAt = jwtAuth.getToken().getExpiresAt();
            if (expiresAt == null || !Instant.now().isBefore(expiresAt)) {
                reject(response, "Unauthorized");
                return;
            }
            String sessionUserId;
            try {
                sessionUserId = objectMapper.readTree(sessionRaw).path("userId").asString();
            } catch (RuntimeException exception) {
                reject(response, "Unauthorized");
                return;
            }
            if (!jwtAuth.getToken().getSubject().equals(sessionUserId)) {
                reject(response, "Unauthorized");
                return;
            }
            try {
                if (!users.existsByIdAndStatus(Long.valueOf(sessionUserId), "ACTIVE")) {
                    reject(response, "Unauthorized");
                    return;
                }
            } catch (NumberFormatException exception) {
                reject(response, "Unauthorized");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> apiRes =
                ApiResponse.error(
                        HttpStatus.UNAUTHORIZED, message != null ? message : "Unauthorized");

        response.getWriter().write(objectMapper.writeValueAsString(apiRes));
    }
}
