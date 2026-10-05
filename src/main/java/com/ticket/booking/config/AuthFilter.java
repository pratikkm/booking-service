package com.ticket.booking.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthFilter extends OncePerRequestFilter {
    private final String adminToken;

    public AuthFilter(@Value("${app.auth.admin-token}") String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/") || uri.equals("/error") || isPublic(request);
    }

    private boolean isPublic(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals("/live") || uri.equals("/ready")
                || (request.getMethod().equals("GET") && uri.startsWith("/shows/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractBearer(request.getHeader("Authorization"));
        String uri = request.getRequestURI();

        if (request.getMethod().equals("POST") && uri.equals("/shows")) {
            if (token == null || !constantTimeEquals(adminToken, token)) {
                writeUnauthorized(response, "admin authorization required");
                return;
            }
            request.setAttribute(AuthContext.USER_ID_ATTRIBUTE, "admin");
            filterChain.doFilter(request, response);
            return;
        }

        if (uri.matches("/shows/[^/]+/reserve") || uri.matches("/reservations/[^/]+/cancel")) {
            if (token == null) {
                writeUnauthorized(response, "bearer token required");
                return;
            }
            if (token.length() > 200) {
                writeUnauthorized(response, "bearer token too long");
                return;
            }
            request.setAttribute(AuthContext.USER_ID_ATTRIBUTE, token);
            filterChain.doFilter(request, response);
            return;
        }

        // Any other non-public API endpoint is rejected rather than treated as anonymous.
        writeUnauthorized(response, "authentication required");
    }

    private static String extractBearer(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }

    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}");
    }
}
