package com.tripbudget.tripbudget_core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.util.WebUtils;
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
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RedisSessionValidationFilter redisSessionValidationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final PolicyAcceptanceFilter policyAcceptanceFilter;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

     public SecurityConfig(
            RedisSessionValidationFilter redisSessionValidationFilter,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler,
            PolicyAcceptanceFilter policyAcceptanceFilter
    ) {
        this.redisSessionValidationFilter = redisSessionValidationFilter;
        this.customAuthenticationEntryPoint = authenticationEntryPoint;
        this.customAccessDeniedHandler = accessDeniedHandler;
        this.policyAcceptanceFilter = policyAcceptanceFilter;
    }

    @Bean
    public CookieCsrfTokenRepository realtimeCsrfTokens() {
        var csrfCookies = new CookieCsrfTokenRepository();
        csrfCookies.setCookieName("WS-XSRF-TOKEN");
        csrfCookies.setCookiePath("/");
        csrfCookies.setCookieCustomizer(cookie -> cookie.httpOnly(true).sameSite("Lax"));
        return csrfCookies;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain realtimeHttpSecurity(HttpSecurity http, CookieCsrfTokenRepository csrfCookies) throws Exception {
        var bearerResolver = new DefaultBearerTokenResolver();
        BearerTokenResolver resolver = request -> {
            String bearer = bearerResolver.resolve(request);
            if (bearer != null) return bearer;
            var cookie = WebUtils.getCookie(request, "access_token");
            return cookie == null ? null : cookie.getValue();
        };
        return http.securityMatcher("/ws", "/realtime/bootstrap")
                .csrf(csrf -> csrf.csrfTokenRepository(csrfCookies))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.bearerTokenResolver(resolver)
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler))
                .addFilterAfter(redisSessionValidationFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(policyAcceptanceFilter, RedisSessionValidationFilter.class)
                .build();
    }

    @Bean
    public FilterRegistrationBean<RedisSessionValidationFilter> redisSessionFilterRegistration() {
        var registration = new FilterRegistrationBean<>(redisSessionValidationFilter);
        registration.setEnabled(false);
        return registration;
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
                        .requestMatchers("/test/**", "/api/test/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/public/trips/*", "/public/plan-templates", "/public/plan-templates/*").permitAll()
                        .anyRequest().authenticated()
                )
                // Regis to verify JWT token to header.payload.signature
                .oauth2ResourceServer(oauth2 -> oauth2
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

                .build();
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
