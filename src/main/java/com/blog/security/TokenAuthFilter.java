package com.blog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class TokenAuthFilter extends OncePerRequestFilter {
    public static final String TOKEN_KEY_PREFIX = "auth:token:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TokenAuthFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            String json = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
            if (StringUtils.hasText(json)) {
                LoginUser stored = objectMapper.readValue(json, LoginUser.class);
                LoginUser loginUser = new LoginUser(stored.userId(), stored.username(), stored.nickname(), stored.role(), stored.loginAt(), token);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        loginUser,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.role().name()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
