package com.runvas.auth.service;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class TokenBlacklistServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final TokenBlacklistService tokenBlacklistService =
            new TokenBlacklistService(redisTemplate, jwtProvider);

    @Test
    void blacklistsTokenWithRemainingTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtProvider.getExpiration("token-1")).thenReturn(Instant.now().plusSeconds(120));

        tokenBlacklistService.blacklist("token-1");

        verify(valueOperations).set(eq("auth:blacklist:token-1"), eq("1"), any(Duration.class));
    }

    @Test
    void doesNotBlacklistAlreadyExpiredToken() {
        when(jwtProvider.getExpiration("expired-token")).thenReturn(Instant.now().minusSeconds(1));

        tokenBlacklistService.blacklist("expired-token");

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void isBlacklistedReturnsTrueWhenKeyExists() {
        when(redisTemplate.hasKey("auth:blacklist:token-2")).thenReturn(true);

        assertThat(tokenBlacklistService.isBlacklisted("token-2")).isTrue();
    }

    @Test
    void isBlacklistedReturnsFalseWhenKeyMissing() {
        when(redisTemplate.hasKey("auth:blacklist:token-3")).thenReturn(false);

        assertThat(tokenBlacklistService.isBlacklisted("token-3")).isFalse();
    }
}
