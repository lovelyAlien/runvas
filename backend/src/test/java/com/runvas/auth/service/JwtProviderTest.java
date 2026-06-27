package com.runvas.auth.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    @Test
    void createsTokenContainingUserId() {
        JwtProvider jwtProvider = new JwtProvider("dev-secret-dev-secret-dev-secret-dev-secret", 3600);
        UUID userId = UUID.randomUUID();

        String token = jwtProvider.createAccessToken(userId);

        assertThat(jwtProvider.parseUserId(token)).isEqualTo(userId);
    }
}
