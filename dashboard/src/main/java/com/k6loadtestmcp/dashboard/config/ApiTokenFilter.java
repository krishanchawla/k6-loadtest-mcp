package com.k6loadtestmcp.dashboard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards /api/** with a plain shared bearer token (DASHBOARD_API_TOKEN), independent of the
 * session/Basic-auth chain that protects the human-facing pages -- see SecurityConfig. This is a
 * machine-to-machine credential the MCP server's publish_report tool sends
 * (K6_LOADTEST_DASHBOARD_TOKEN on that side), not a user login.
 *
 * Deliberately NOT a Spring bean (no @Component/@Bean) -- Spring Boot auto-registers any
 * Filter-typed bean as a *global* servlet filter applied to every request, which would run this
 * ahead of/outside the "webFilterChain" too and reject the human-facing pages (observed during
 * manual testing: GET / was rejected with 401 and no WWW-Authenticate header, i.e. by this filter,
 * not by Spring Security's Basic auth entry point). SecurityConfig constructs it directly and wires
 * it into apiFilterChain only, via addFilterBefore.
 */
public class ApiTokenFilter extends OncePerRequestFilter {

    // Generous for a RunMetrics payload with a handful of endpoints/thresholds; cheap first line of
    // defense against a garbage-sized POST body. This only catches requests that send a Content-Length
    // header -- a chunked-encoding request could skip it. nginx's client_max_body_size in front of this
    // (see README) is the robust enforcement point; this is defense-in-depth, not the primary guard.
    private static final long MAX_BODY_BYTES = 65_536;

    private final String expectedToken;

    public ApiTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_BODY_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body too large");
            return;
        }

        if (expectedToken == null || expectedToken.isBlank()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "DASHBOARD_API_TOKEN is not configured on the server");
            return;
        }

        String header = request.getHeader("Authorization");
        String provided = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;

        if (provided == null || !constantTimeEquals(provided, expectedToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid bearer token");
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
