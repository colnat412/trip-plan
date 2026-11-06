package com.tripbudget.tripbudget_core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import com.tripbudget.tripbudget_core.maps.web.MapsRequestFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Set<String> PUBLIC_AUTH_ENDPOINTS = Set.of("/auth/register", "/auth/login", "/auth/google",
            "/auth/verify-otp", "/auth/resend-otp", "/auth/refresh");
    private final DefaultBearerTokenResolver bearerTokens = new DefaultBearerTokenResolver();

    private final RedisSessionValidationFilter redisSessionValidationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final PolicyAcceptanceFilter policyAcceptanceFilter;
    private final MapsRequestFilter mapsRequestFilter;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

     public SecurityConfig(
            RedisSessionValidationFilter redisSessionValidationFilter,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler,
            PolicyAcceptanceFilter policyAcceptanceFilter,
            MapsRequestFilter mapsRequestFilter
    ) {
        this.redisSessionValidationFilter = redisSessionValidationFilter;
        this.customAuthenticationEntryPoint = authenticationEntryPoint;
        this.customAccessDeniedHandler = accessDeniedHandler;
        this.policyAcceptanceFilter = policyAcceptanceFilter;
        this.mapsRequestFilter = mapsRequestFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS.toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.GET, "/ai/health", "/ai/test").permitAll()
                        .requestMatchers("/test/**", "/api/test/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/public/trips/*", "/public/plan-templates", "/public/plan-templates/*").permitAll()
                        .anyRequest().authenticated()
                )
                // Regis to verify JWT token to header.payload.signature
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(this::resolveBearerToken)
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                // verify session redis
                .addFilterAfter(
                        redisSessionValidationFilter,
                        BearerTokenAuthenticationFilter.class
                )
                .addFilterAfter(policyAcceptanceFilter, RedisSessionValidationFilter.class)
                .addFilterAfter(mapsRequestFilter, AuthorizationFilter.class)

                .build();
    }

    private String resolveBearerToken(HttpServletRequest request) {
        // Nest's @Public endpoints did not attempt to authenticate an optional bearer header.
        if ("POST".equals(request.getMethod()) && PUBLIC_AUTH_ENDPOINTS.contains(request.getServletPath())) {
            return null;
        }
        return bearerTokens.resolve(request);
    }

    @Bean
    public FilterRegistrationBean<RedisSessionValidationFilter> sessionFilterRegistration() {
        var registration = new FilterRegistrationBean<>(redisSessionValidationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<MapsRequestFilter> mapsFilterRegistration() {
        var registration = new FilterRegistrationBean<>(mapsRequestFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<PolicyAcceptanceFilter> policyFilterRegistration() {
        var registration = new FilterRegistrationBean<>(policyAcceptanceFilter);
        registration.setEnabled(false); // Run only in the security chain, after JWT/session validation.
        return registration;
    }

    // Verify signature from secret key, expire
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey secretKey = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(JwtValidators.createDefault());

        return decoder;
    }
}
