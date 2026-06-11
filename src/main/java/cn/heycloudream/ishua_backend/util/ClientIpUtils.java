package cn.heycloudream.ishua_backend.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Resolves the client IP for upstream-proxied requests.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClientIpUtils {

    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String cfConnectingIp = trimToNull(request.getHeader("CF-Connecting-IP"));
        if (cfConnectingIp != null) {
            return cfConnectingIp;
        }

        String forwardedFor = trimToNull(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            int commaIndex = forwardedFor.indexOf(',');
            if (commaIndex > 0) {
                return forwardedFor.substring(0, commaIndex).trim();
            }
            return forwardedFor;
        }

        return request.getRemoteAddr();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
