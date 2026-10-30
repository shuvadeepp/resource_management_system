package tech.csm.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client IP: X-Forwarded-For ki pehli value (proxy ke peeche), warna remoteAddr.
 * IPv6 localhost (0:0:0:0:0:0:0:1 / ::1) -> 127.0.0.1.
 * Note: X-Forwarded-For client khud bhi bhej sakta hai - sirf apne proxy ke peeche bharosemand hai.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            ip = forwarded.split(",")[0].trim();
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        // ip_address VARCHAR(45)
        return ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
