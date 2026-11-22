package com.ashutosh.banking.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {
    private StringRedisTemplate redis;
    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        filter = new RateLimitFilter(redis);
        chain = mock(FilterChain.class);
    }

    @Test
    void healthChecksNeverDependOnRedis() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(redis);
    }

    @Test
    void rejectsAuthenticationRequestsAfterThreshold() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(11L);
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("10", response.getHeader("X-RateLimit-Limit"));
        verifyNoInteractions(chain);
    }

    @Test
    void redisFailureClosesProtectedEndpoints() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class)))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        var request = new MockHttpServletRequest("POST", "/api/transfers");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void forwardedAddressCannotSpoofRateLimitIdentity() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(1L);
        var request = new MockHttpServletRequest("GET", "/api/accounts/demo");
        request.setRemoteAddr("127.0.0.9");
        request.addHeader("X-Forwarded-For", "203.0.113.200");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(redis).execute(any(RedisScript.class),
                eq(List.of("vaultcore:rate:banking:127.0.0.9")), eq("60"));
        verify(chain).doFilter(request, response);
    }
}
