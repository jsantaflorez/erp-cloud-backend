package com.erp.erp_cloud.security.filter;

import com.erp.erp_cloud.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP sliding-window rate limit for the public auth endpoints
 * (audit 2026-11-02, A2): POST /api/auth/login and POST /api/auth/companies.
 *
 * In-memory, so the limit is per application instance. That is enough for
 * a single-server pilot; with several instances behind a load balancer move
 * this to a shared store (Redis/Bucket4j) or to the gateway/WAF.
 *
 * Behind a reverse proxy set app.security.rate-limit.trust-forwarded-for=true
 * so the client IP comes from X-Forwarded-For instead of the proxy's address.
 *
 * FIX (2026-11-19, auditoria F1-26 / Bloque 0.6): the client IP used to be
 * the FIRST value of X-Forwarded-For. That value is written by the client
 * itself (proxies APPEND to the header), so changing it on every request
 * removed the limit entirely. The IP is now read from the RIGHT: proxies we
 * trust each append one entry, so the real client is the entry
 * app.security.rate-limit.trusted-proxy-hops positions from the end
 * (1 = the last entry = what our own front proxy saw). Anything a client
 * puts in the header lands to the left of that and is ignored.
 * Note: this needs the raw header, so the prod profile must not run
 * Spring's ForwardedHeaderFilter (server.forward-headers-strategy=framework
 * hides the header and rewrites getRemoteAddr() from its FIRST value).
 */
@Slf4j
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final long WINDOW_MS = 60_000L;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int loginPerMinute;
    private final int companyLookupPerMinute;
    private final boolean trustForwardedFor;
    private final int trustedProxyHops;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.login-per-minute:5}") int loginPerMinute,
            @Value("${app.security.rate-limit.company-lookup-per-minute:20}") int companyLookupPerMinute,
            @Value("${app.security.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor,
            @Value("${app.security.rate-limit.trusted-proxy-hops:1}") int trustedProxyHops) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.loginPerMinute = loginPerMinute;
        this.companyLookupPerMinute = companyLookupPerMinute;
        this.trustForwardedFor = trustForwardedFor;
        this.trustedProxyHops = Math.max(1, trustedProxyHops);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        return !("/api/auth/login".equals(path) || "/api/auth/companies".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        int limit = path.endsWith("/login") ? loginPerMinute : companyLookupPerMinute;
        String clientIp = clientIp(request);

        if (!tryAcquire(path + "|" + clientIp, limit, System.currentTimeMillis())) {
            log.warn("AUTH_RATE_LIMITED | path: {} | ipHash: {}", path, clientIp.hashCode());
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(WINDOW_MS / 1000));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error("TOO_MANY_REQUESTS")));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Sliding window: true when this key has fewer than {@code limit} hits in the last minute. */
    boolean tryAcquire(String key, int limit, long nowMs) {
        if (hits.size() > CLEANUP_THRESHOLD) {
            cleanup(nowMs);
        }
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            prune(window, nowMs);
            if (window.size() >= limit) {
                return false;
            }
            window.addLast(nowMs);
            return true;
        }
    }

    private void cleanup(long nowMs) {
        hits.entrySet().removeIf(entry -> {
            Deque<Long> window = entry.getValue();
            synchronized (window) {
                prune(window, nowMs);
                return window.isEmpty();
            }
        });
    }

    private static void prune(Deque<Long> window, long nowMs) {
        while (!window.isEmpty() && nowMs - window.peekFirst() >= WINDOW_MS) {
            window.pollFirst();
        }
    }

    String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                // Right to left: the entry trustedProxyHops from the end.
                // A header shorter than that means the request did not come
                // through all our proxies; fall back to the leftmost entry
                // we have rather than to nothing.
                int index = Math.max(0, hops.length - trustedProxyHops);
                String ip = hops[index].trim();
                if (!ip.isEmpty()) {
                    return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
