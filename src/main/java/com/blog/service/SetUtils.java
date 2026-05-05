package com.blog.service;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

final class SetUtils {
    private SetUtils() {
    }

    static void deleteByPattern(StringRedisTemplate redisTemplate, String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
