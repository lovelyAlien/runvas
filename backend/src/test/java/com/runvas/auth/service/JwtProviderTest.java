package com.runvas.auth.service;

import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    @Test
    void createsTokenContainingUserId() {
        JwtProvider jwtProvider = new JwtProvider("dev-secret-dev-secret-dev-secret-dev-secret", 3600);
        UUID userId = UUID.randomUUID();

        String token = jwtProvider.createAccessToken(userId);

        assertThat(jwtProvider.parseUserId(token)).isEqualTo(userId);
    }

    @Test
    void rejectsMalformedToken() {
        JwtProvider jwtProvider = new JwtProvider("dev-secret-dev-secret-dev-secret-dev-secret", 3600);

        assertThatThrownBy(() -> jwtProvider.parseUserId("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsBlankSecret() {
        assertThatThrownBy(() -> new JwtProvider(" ", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT secret must not be blank");
    }

    @Test
    void rejectsTooShortSecret() {
        assertThatThrownBy(() -> new JwtProvider("too-short", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT secret must be at least 32 bytes");
    }

    @Test
    void returnsTokenExpirationInstant() {
        JwtProvider jwtProvider = new JwtProvider("dev-secret-dev-secret-dev-secret-dev-secret", 3600);
        UUID userId = UUID.randomUUID();
        Instant beforeCreate = Instant.now();

        String token = jwtProvider.createAccessToken(userId);
        Instant expiration = jwtProvider.getExpiration(token);

        assertThat(expiration).isAfter(beforeCreate.plusSeconds(3599));
        assertThat(expiration).isBefore(beforeCreate.plusSeconds(3601));
    }
}
