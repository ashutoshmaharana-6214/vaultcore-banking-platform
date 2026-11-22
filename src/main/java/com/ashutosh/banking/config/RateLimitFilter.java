package com.ashutosh.banking.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int WINDOW_SECONDS = 60;
    private static final RedisScript<Long> COUNTER_SCRIPT = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]); " +
            "if n == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end; " +
            "return n", Long.class);

    private final StringRedisTemplate redis;

    public RateLimitFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // Use route groups to avoid unbounded keys derived from arbitrary URL paths.
        String group = path.startsWith("/api/auth/") ? "auth"
                : path.startsWith("/api/transfers") ? "transfers" : "banking";
        int limit = group.equals("auth") ? 10 : 60;
        String key = "vaultcore:rate:" + group + ":" + request.getRemoteAddr();

        try {
            Long count = redis.execute(COUNTER_SCRIPT, List.of(key), String.valueOf(WINDOW_SECONDS));
            if (count == null) throw new IllegalStateException("Redis returned no rate-limit value");
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Window", WINDOW_SECONDS + " seconds");
            if (count > limit) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
                return;
            }
        } catch (RuntimeException redisFailure) {
            // Fail closed: no unmetered authentication attempts or transfers when Redis is unavailable.
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"API temporarily unavailable\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
