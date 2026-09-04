package com.runvas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        AppleTokenExchangeClient appleTokenExchangeClient = mock(AppleTokenExchangeClient.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);

        when(appleAuthClient.verifyIdentityToken("token-abc"))
                .thenReturn(new AppleUserInfo("apple-sub-1", "runner@example.com"));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, "apple-sub-1"))
                .thenReturn(Optional.empty());
        User savedUser = persisted(User.createAppleUser("apple-sub-1", "runner@example.com", "Seoul Runner"));
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);
        when(jwtProvider.createAccessToken(any())).thenReturn("jwt-token");

        AppleAuthService service =
                new AppleAuthService(appleAuthClient, appleTokenExchangeClient, userRepository, jwtProvider);
        AuthResponse response =
                service.login(new AppleLoginRequest("APPLE", "token-abc", "auth-code-1", "Seoul Runner"));

        assertThat(response.isNewUser()).isTrue();
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.user().nickname()).isEqualTo("Seoul Runner");
    }

    @Test
    void login_토큰_교환에_성공하면_refresh_token을_저장한다() {
        AppleAuthClient appleAuthClient = mock(AppleAuthClient.class);
        AppleTokenExchangeClient appleTokenExchangeClient = mock(AppleTokenExchangeClient.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);

        when(appleAuthClient.verifyIdentityToken("token-abc"))
                .thenReturn(new AppleUserInfo("apple-sub-2", "runner@example.com"));
        User savedUser = persisted(User.createAppleUser("apple-sub-2", "runner@example.com", "Seoul Runner"));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, "apple-sub-2"))
                .thenReturn(Optional.of(savedUser));
        when(appleTokenExchangeClient.exchangeForRefreshToken("auth-code-2"))
                .thenReturn("apple-refresh-token-2");
        when(jwtProvider.createAccessToken(any())).thenReturn("jwt-token");

        AppleAuthService service =
                new AppleAuthService(appleAuthClient, appleTokenExchangeClient, userRepository, jwtProvider);
        service.login(new AppleLoginRequest("APPLE", "token-abc", "auth-code-2", null));

        assertThat(savedUser.getAppleRefreshToken()).isEqualTo("apple-refresh-token-2");
        verify(userRepository).save(savedUser);
    }

    @Test
    void login_토큰_교환이_실패해도_로그인은_성공한다() {
        AppleAuthClient appleAuthClient = mock(AppleAuthClient.class);
        AppleTokenExchangeClient appleTokenExchangeClient = mock(AppleTokenExchangeClient.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);

        when(appleAuthClient.verifyIdentityToken("token-abc"))
                .thenReturn(new AppleUserInfo("apple-sub-3", "runner@example.com"));
        User savedUser = persisted(User.createAppleUser("apple-sub-3", "runner@example.com", "Seoul Runner"));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, "apple-sub-3"))
                .thenReturn(Optional.of(savedUser));
        when(appleTokenExchangeClient.exchangeForRefreshToken("auth-code-3"))
                .thenThrow(new IllegalStateException("apple token endpoint down"));
        when(jwtProvider.createAccessToken(any())).thenReturn("jwt-token");

        AppleAuthService service =
                new AppleAuthService(appleAuthClient, appleTokenExchangeClient, userRepository, jwtProvider);
        AuthResponse response =
                service.login(new AppleLoginRequest("APPLE", "token-abc", "auth-code-3", null));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(savedUser.getAppleRefreshToken()).isNull();
    }

    private static User persisted(User user) {
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-06-22T08:00:00Z"));
        ReflectionTestUtils.setField(user, "updatedAt", Instant.parse("2026-06-22T08:00:00Z"));
        return user;
    }
}
