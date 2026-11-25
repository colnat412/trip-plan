package com.tripbudget.tripbudget_core.auth.session;

import java.util.Locale;

/** Display hints only; client headers never grant permissions or identify a user. */
public record SessionClientInfo(String clientType, String browser, String operatingSystem,
                                String deviceType, String loginMethod, String userAgent) {
    public static SessionClientInfo unknown() {
        return new SessionClientInfo("UNKNOWN", "Unknown", "Unknown", "UNKNOWN", "UNKNOWN", "");
    }

    public static SessionClientInfo from(String agent, String client, String path) {
        String ua = agent == null ? "" : agent.replaceAll("[\\p{Cntrl}]", " ").trim();
        ua = ua.substring(0, Math.min(ua.length(), 512));
        String lower = ua.toLowerCase(Locale.ROOT);
        String browser = lower.contains("edg/") ? "Edge" : lower.contains("opr/") ? "Opera"
                : lower.contains("firefox/") || lower.contains("fxios/") ? "Firefox"
                : lower.contains("chrome/") || lower.contains("crios/") ? "Chrome"
                : lower.contains("safari/") ? "Safari" : "Unknown";
        String os = lower.contains("android") ? "Android"
                : lower.contains("iphone") || lower.contains("ipad") ? "iOS"
                : lower.contains("windows") ? "Windows" : lower.contains("macintosh") ? "macOS"
                : lower.contains("linux") ? "Linux" : "Unknown";
        String device = lower.contains("ipad") || lower.contains("tablet") || (lower.contains("android") && !lower.contains("mobile")) ? "TABLET"
                : lower.contains("mobile") || lower.contains("iphone") ? "MOBILE"
                : !"Unknown".equals(os) ? "DESKTOP" : "UNKNOWN";
        String type = java.util.Set.of("ADMIN", "WEB", "PWA").contains(client == null ? "" : client) ? client : "UNKNOWN";
        String method = path != null && path.endsWith("/google") ? "GOOGLE"
                : path != null && path.endsWith("/verify-otp") ? "OTP"
                : path != null && path.endsWith("/login") ? "PASSWORD" : "UNKNOWN";
        return new SessionClientInfo(type, browser, os, device, method, ua);
    }
}
