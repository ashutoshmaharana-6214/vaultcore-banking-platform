package com.ashutosh.banking.config;
import jakarta.servlet.*;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.http.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Duration;
@Component
public class RateLimitFilter extends OncePerRequestFilter {
 private final StringRedisTemplate redis;
 public RateLimitFilter(StringRedisTemplate redis){this.redis=redis;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  String path=request.getRequestURI();
  if(!path.startsWith("/api/")){chain.doFilter(request,response);return;}
  String client=request.getRemoteAddr(); String key="rate-limit:"+client+":"+path;
  try {
   Long count=redis.opsForValue().increment(key);
   if(count!=null && count==1L) redis.expire(key,Duration.ofMinutes(1));
   response.setHeader("X-RateLimit-Limit","60"); response.setHeader("X-RateLimit-Window","60 seconds");
   if(count!=null && count>60){response.setStatus(429);response.setContentType("application/json");response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again later.\"}");return;}
  } catch(RuntimeException redisUnavailable) {
   response.setHeader("X-RateLimit-Status","temporarily-unavailable-fail-open");
  }
  chain.doFilter(request,response);
 }
}
