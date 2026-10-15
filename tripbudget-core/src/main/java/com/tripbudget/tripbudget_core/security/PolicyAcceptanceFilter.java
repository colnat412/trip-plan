package com.tripbudget.tripbudget_core.security;

import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PolicyAcceptanceFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public PolicyAcceptanceFilter(UserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            Long userId;
            try { userId = Long.valueOf(jwt.getToken().getSubject()); }
            catch (NumberFormatException error) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            if (!users.hasAcceptedPolicies(userId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"status\":403,\"code\":\"POLICY_ACCEPTANCE_REQUIRED\",\"message\":\"Accept the policies before using Trip Plan\",\"data\":null}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
