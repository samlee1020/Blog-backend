package com.blog.security;

import com.blog.domain.UserRole;

import java.time.LocalDateTime;

public record LoginUser(
        Long userId,
        String username,
        String nickname,
        UserRole role,
        LocalDateTime loginAt,
        String token
) {
}
