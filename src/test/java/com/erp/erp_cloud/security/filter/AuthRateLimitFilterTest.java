package com.erp.erp_cloud.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the auth endpoints rate limit (audit 2026-11-02, A2).
 */
class AuthRateLimitFilterTest {

    private AuthRateLimitFilter filter(int loginPerMinute) {
        return new AuthRateLimitFilter(new ObjectMapper(), true, loginPerMinute, 20, false);
    }

    @Test
    @DisplayName("Allows up to the limit inside the window, then rejects")
    void slidingWindowLimit() {
        AuthRateLimitFilter f = filter(3);
        long t0 = 1_000_000L;
        assertThat(f.tryAcquire("k", 3, t0)).isTrue();
        assertThat(f.tryAcquire("k", 3, t0 + 1)).isTrue();
        assertThat(f.tryAcquire("k", 3, t0 + 2)).isTrue();
        assertThat(f.tryAcquire("k", 3, t0 + 3)).isFalse();
        // the first hit leaves the window after 60 s
        assertThat(f.tryAcquire("k", 3, t0 + AuthRateLimitFilter.WINDOW_MS)).isTrue();
        // other keys (other IPs) are independent
        assertThat(f.tryAcquire("other", 3, t0 + 3)).isTrue();
    }

    @Test
    @DisplayName("Returns 429 with TOO_MANY_REQUESTS once the login limit is exceeded")
    void returns429() throws Exception {
        AuthRateLimitFilter f = filter(1);
        MockHttpServletResponse first = new MockHttpServletResponse();
        f.doFilter(loginRequest(), first, new MockFilterChain());
        assertThat(first.getStatus()).isEqualTo(200);

        MockHttpServletResponse second = new MockHttpServletResponse();
        f.doFilter(loginRequest(), second, new MockFilterChain());
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getContentAsString()).contains("TOO_MANY_REQUESTS");
        assertThat(second.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    @DisplayName("Business endpoints are never rate limited by this filter")
    void ignoresOtherPaths() throws Exception {
        AuthRateLimitFilter f = filter(1);
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/journal-entries");
            req.setServletPath("/api/v1/journal-entries");
            MockHttpServletResponse res = new MockHttpServletResponse();
            f.doFilter(req, res, new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletRequest loginRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setServletPath("/api/auth/login");
        req.setRemoteAddr("10.0.0.1");
        return req;
    }
}
