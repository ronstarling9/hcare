package com.hcare.api.v1.family;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class VerifyRateLimiter extends OncePerRequestFilter {

    private static final String TARGET_PATH = "/api/v1/family/auth/verify";
    private static final String TARGET_METHOD = "POST";
    private static final int MAX_PER_MINUTE = 10;
    private static final DateTimeFormatter MINUTE_FMT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    // Key: "IP:yyyyMMddHHmm" — one counter per IP per minute.
    // The map grows at most (active IPs * minutes in window). Entries become stale after
    // one minute and are evicted lazily when the map exceeds 10,000 entries.
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    // When true, trust the X-Real-IP header set by the reverse proxy.
    // ONLY enable this when the backend is not directly reachable by clients (i.e., all
    // traffic flows through a trusted proxy that sets X-Real-IP from the actual client IP).
    // X-Forwarded-For is intentionally NOT used: it is trivially spoofable by clients
    // who can include it in their own requests, defeating the rate limiter entirely.
    private final boolean trustedProxy;

    public VerifyRateLimiter(
            @org.springframework.beans.factory.annotation.Value(
                "${hcare.portal.trusted-proxy:false}") boolean trustedProxy) {
        this.trustedProxy = trustedProxy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!TARGET_METHOD.equals(request.getMethod())
                || !request.getRequestURI().equals(TARGET_PATH)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        String minute = ZonedDateTime.now(ZoneOffset.UTC).format(MINUTE_FMT);
        String key = ip + ":" + minute;

        // Lazy eviction: if map is large, clear stale minute entries.
        if (counters.size() > 10_000) {
            String currentMinute = minute;
            counters.entrySet().removeIf(e -> !e.getKey().endsWith(":" + currentMinute));
        }

        AtomicInteger count = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
        if (count.incrementAndGet() > MAX_PER_MINUTE) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests\",\"status\":429}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        // Use the TCP peer address — cannot be spoofed by clients.
        // If deployed behind a trusted reverse proxy that sets X-Real-IP,
        // configure hcare.portal.trusted-proxy: true and ensure the proxy
        // is the only entry point (never expose the backend directly).
        // X-Forwarded-For is intentionally NOT used: clients can inject arbitrary
        // values into that header, making per-IP rate limiting trivially bypassable.
        if (trustedProxy) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
