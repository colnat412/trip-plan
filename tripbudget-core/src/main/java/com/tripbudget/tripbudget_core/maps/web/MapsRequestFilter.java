package com.tripbudget.tripbudget_core.maps.web;

import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import com.tripbudget.tripbudget_core.maps.config.MapsSettings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

@Component
public class MapsRequestFilter extends OncePerRequestFilter {
    private static final int BODY_LIMIT = 96 * 1024;
    private final MapsSettings settings;
    private final ObjectMapper mapper;
    public MapsRequestFilter(MapsSettings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/maps/")) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Cache-Control", "private, no-store");
        boolean trustedTls = settings.isTrustProxy() && "https".equals(request.getHeader("X-Forwarded-Proto"));
        if (settings.isRequireHttps() && !request.isSecure() && !trustedTls) {
            reject(response, HttpStatus.UPGRADE_REQUIRED, "MAPS_HTTPS_REQUIRED", "HTTPS is required for map requests.");
            return;
        }
        if ("POST".equals(request.getMethod()) && "/maps/distance".equals(path)) {
            if (request.getContentLengthLong() > BODY_LIMIT) {
                reject(response, HttpStatus.PAYLOAD_TOO_LARGE, "MAPS_BODY_TOO_LARGE", "Map request body is too large.");
                return;
            }
            byte[] bytes = request.getInputStream().readNBytes(BODY_LIMIT + 1);
            if (bytes.length > BODY_LIMIT) {
                reject(response, HttpStatus.PAYLOAD_TOO_LARGE, "MAPS_BODY_TOO_LARGE", "Map request body is too large.");
                return;
            }
            chain.doFilter(new BufferedBody(request, bytes), response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(mapper.writeValueAsString(ApiResponse.failure(status, message, Map.of("code", code))));
    }

    private static class BufferedBody extends HttpServletRequestWrapper {
        private final byte[] bytes;
        BufferedBody(HttpServletRequest request, byte[] bytes) {
            super(request);
            this.bytes = bytes;
        }
        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] target, int offset, int length) {
                    return input.read(target, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Synchronous JSON requests only");
                }
            };
        }
    }
}
