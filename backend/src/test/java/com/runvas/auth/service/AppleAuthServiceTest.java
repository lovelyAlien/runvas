package com.runvas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.runvas.auth.dto.AppleLoginRequest;
import com.runvas.auth.dto.AuthResponse;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AppleAuthServiceTest {

    @Test
    void login_신규_사용자면_생성하고_isNewUser는_true다() {
        AppleAuthClient appleAuthClient = mock(AppleAuthClient.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);

        when(appleAuthClient.verifyIdentityToken("token-abc"))
                .thenReturn(new AppleUserInfo("apple-sub-1", "runner@example.com"));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, "apple-sub-1"))
                .thenReturn(Optional.empty());
        User savedUser = persisted(User.createAppleUser("apple-sub-1", "runner@example.com", "Seoul Runner"));
        when(userRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(User.class)))
                .thenReturn(savedUser);
        when(jwtProvider.createAccessToken(org.mockito.ArgumentMatchers.any()))
                .thenReturn("jwt-token");

        AppleAuthService service = new AppleAuthService(appleAuthClient, userRepository, jwtProvider);
        AuthResponse response = service.login(new AppleLoginRequest("APPLE", "token-abc", "Seoul Runner"));

        assertThat(response.isNewUser()).isTrue();
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.user().nickname()).isEqualTo("Seoul Runner");
    }

    private static User persisted(User user) {
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-06-22T08:00:00Z"));
        ReflectionTestUtils.setField(user, "updatedAt", Instant.parse("2026-06-22T08:00:00Z"));
        return user;
    }
}
