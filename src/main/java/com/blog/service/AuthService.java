package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BizException;
import com.blog.common.ErrorCode;
import com.blog.config.AppProperties;
import com.blog.domain.User;
import com.blog.domain.UserRole;
import com.blog.domain.UserStatus;
import com.blog.dto.Requests;
import com.blog.mapper.UserMapper;
import com.blog.security.LoginUser;
import com.blog.security.TokenAuthFilter;
import com.blog.vo.Views;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final Duration LOGIN_RATE_WINDOW = Duration.ofMinutes(15);
    private static final String USER_TOKEN_KEY_PREFIX = "auth:user:";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, StringRedisTemplate redisTemplate,
                       ObjectMapper objectMapper, AppProperties properties) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public Views.UserView registerGuest(Requests.GuestRegisterRequest request) {
        if (findActiveByUsername(request.username()) != null) {
            throw new BizException(ErrorCode.CONFLICT, "username already exists");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname() : request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.GUEST);
        user.setStatus(UserStatus.ACTIVE);
        userMapper.insert(user);
        return toUserView(user);
    }

    @Transactional
    public Views.LoginView login(Requests.LoginRequest request, HttpServletRequest servletRequest) {
        String ip = clientIp(servletRequest);
        String rateKey = "rate:login:" + request.username() + ":" + ip;
        String failures = redisTemplate.opsForValue().get(rateKey);
        if (failures != null && Integer.parseInt(failures) >= MAX_LOGIN_FAILURES) {
            throw new BizException(ErrorCode.RATE_LIMITED, "too many login failures");
        }

        User user = findActiveByUsername(request.username());
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            Long count = redisTemplate.opsForValue().increment(rateKey);
            if (count != null && count == 1) {
                redisTemplate.expire(rateKey, LOGIN_RATE_WINDOW);
            }
            throw new BizException(ErrorCode.UNAUTHORIZED, "invalid username or password");
        }

        redisTemplate.delete(rateKey);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getNickname(), user.getRole(), LocalDateTime.now(), token);
        try {
            redisTemplate.opsForValue().set(TokenAuthFilter.TOKEN_KEY_PREFIX + token, objectMapper.writeValueAsString(loginUser),
                    Duration.ofSeconds(properties.tokenTtlSeconds()));
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "token serialization failed");
        }
        String userTokensKey = userTokensKey(user.getId());
        redisTemplate.opsForSet().add(userTokensKey, token);
        redisTemplate.expire(userTokensKey, Duration.ofSeconds(properties.tokenTtlSeconds()));
        return new Views.LoginView(token, properties.tokenTtlSeconds(), toUserView(user));
    }

    public boolean logout(LoginUser loginUser) {
        redisTemplate.delete(TokenAuthFilter.TOKEN_KEY_PREFIX + loginUser.token());
        redisTemplate.opsForSet().remove(userTokensKey(loginUser.userId()), loginUser.token());
        return true;
    }

    public void invalidateUserTokens(Long userId) {
        String key = userTokensKey(userId);
        Set<String> tokens = redisTemplate.opsForSet().members(key);
        if (tokens != null) {
            tokens.forEach(token -> redisTemplate.delete(TokenAuthFilter.TOKEN_KEY_PREFIX + token));
        }
        redisTemplate.delete(key);
    }

    public Views.UserView toUserView(User user) {
        return new Views.UserView(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }

    public User findActiveByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .isNull(User::getDeletedAt)
                .last("limit 1"));
    }

    private static String userTokensKey(Long userId) {
        return USER_TOKEN_KEY_PREFIX + userId + ":tokens";
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
