package com.runvas.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "auth:blacklist:";
    private static final String USER_BAN_KEY_PREFIX = "auth:banned-user:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProvider jwtProvider;

    public TokenBlacklistService(StringRedisTemplate redisTemplate, JwtProvider jwtProvider) {
        this.redisTemplate = redisTemplate;
        this.jwtProvider = jwtProvider;
    }

    public void blacklist(String token) {
        Instant expiresAt = jwtProvider.getExpiration(token);
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", remaining);
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }

    public void banUser(UUID userId) {
        Duration ttl = Duration.ofSeconds(jwtProvider.getExpirationSeconds()).plusHours(1);
        redisTemplate.opsForValue().set(USER_BAN_KEY_PREFIX + userId, "1", ttl);
    }

    public boolean isUserBanned(UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(USER_BAN_KEY_PREFIX + userId));
    }
}
