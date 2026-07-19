package com.runvas.auth.service;

import com.runvas.auth.dto.AuthResponse;
import com.runvas.auth.dto.KakaoLoginRequest;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KakaoAuthServiceTest {

    private final KakaoAuthClient kakaoAuthClient = mock(KakaoAuthClient.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtProvider jwtProvider = new JwtProvider("dev-secret-dev-secret-dev-secret-dev-secret", 3600);
    private final KakaoAuthService kakaoAuthService = new KakaoAuthService(kakaoAuthClient, userRepository, jwtProvider);

    @Test
    void createsNewKakaoUserAndReturnsRunvasAccessToken() {
        KakaoLoginRequest request = new KakaoLoginRequest("KAKAO", "authorization-code", "runvas://auth/kakao");
        when(kakaoAuthClient.fetchUserInfo("authorization-code", "runvas://auth/kakao"))
                .thenReturn(new KakaoUserInfo("kakao-123", "runner@example.com", "Seoul Runner", null));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-123"))
                .thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        AuthResponse response = kakaoAuthService.login(request);

        UUID tokenUserId = jwtProvider.parseUserId(response.accessToken());
        assertThat(tokenUserId.toString()).isEqualTo(response.user().id().replace("user_", ""));
        assertThat(response.isNewUser()).isTrue();
        assertThat(response.user().email()).isEqualTo("runner@example.com");
        assertThat(response.user().provider()).isEqualTo("KAKAO");
        assertThat(response.user().nickname()).isEqualTo("Seoul Runner");
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void logsInExistingKakaoUserWithoutCreatingAnotherUser() {
        KakaoLoginRequest request = new KakaoLoginRequest("KAKAO", "authorization-code", "runvas://auth/kakao");
        User existingUser = persisted(User.createKakaoUser("kakao-123", "runner@example.com", "Seoul Runner", null));
        when(kakaoAuthClient.fetchUserInfo("authorization-code", "runvas://auth/kakao"))
                .thenReturn(new KakaoUserInfo("kakao-123", "changed@example.com", "Changed", "https://example.com/p.png"));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-123"))
                .thenReturn(Optional.of(existingUser));

        AuthResponse response = kakaoAuthService.login(request);

        assertThat(response.isNewUser()).isFalse();
        assertThat(response.user().email()).isEqualTo("runner@example.com");
        assertThat(response.user().nickname()).isEqualTo("Seoul Runner");
    }

    @Test
    void loginRestoresSoftDeletedUserAndClearsDeletedAt() {
        KakaoLoginRequest request = new KakaoLoginRequest("KAKAO", "authorization-code", "runvas://auth/kakao");
        User withdrawnUser = persisted(User.createKakaoUser("kakao-123", "runner@example.com", "Seoul Runner", null));
        withdrawnUser.markWithdrawn();
        when(kakaoAuthClient.fetchUserInfo("authorization-code", "runvas://auth/kakao"))
                .thenReturn(new KakaoUserInfo("kakao-123", "runner@example.com", "Seoul Runner", null));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-123"))
                .thenReturn(Optional.of(withdrawnUser));

        AuthResponse response = kakaoAuthService.login(request);

        assertThat(response.isNewUser()).isFalse();
        assertThat(withdrawnUser.isDeleted()).isFalse();
        verify(userRepository).save(withdrawnUser);
    }

    @Test
    void treatsDuplicateCreateRaceAsExistingUserLogin() {
        KakaoLoginRequest request = new KakaoLoginRequest("KAKAO", "authorization-code", "runvas://auth/kakao");
        User existingUser = persisted(User.createKakaoUser("kakao-123", "runner@example.com", "Seoul Runner", null));
        when(kakaoAuthClient.fetchUserInfo("authorization-code", "runvas://auth/kakao"))
                .thenReturn(new KakaoUserInfo("kakao-123", "runner@example.com", "Seoul Runner", null));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-123"))
                .thenReturn(Optional.empty(), Optional.of(existingUser));
        when(userRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate provider user"));

        AuthResponse response = kakaoAuthService.login(request);

        UUID tokenUserId = jwtProvider.parseUserId(response.accessToken());
        assertThat(tokenUserId.toString()).isEqualTo(existingUser.getId().toString());
        assertThat(response.isNewUser()).isFalse();
        assertThat(response.user().id()).isEqualTo("user_" + existingUser.getId());
        assertThat(response.user().email()).isEqualTo("runner@example.com");
        assertThat(response.user().nickname()).isEqualTo("Seoul Runner");
    }

    @Test
    void rejectsUnsupportedProvider() {
        KakaoLoginRequest request = new KakaoLoginRequest("APPLE", "authorization-code", "runvas://auth/kakao");

        assertThatThrownBy(() -> kakaoAuthService.login(request))
                .isInstanceOfSatisfying(RunvasException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("provider must be KAKAO");
                });
    }

    @Test
    void userResponseDoesNotExposeProviderUserId() {
        KakaoLoginRequest request = new KakaoLoginRequest("KAKAO", "authorization-code", "runvas://auth/kakao");
        when(kakaoAuthClient.fetchUserInfo("authorization-code", "runvas://auth/kakao"))
                .thenReturn(new KakaoUserInfo("secret-provider-id", "runner@example.com", "Seoul Runner", null));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "secret-provider-id"))
                .thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        AuthResponse response = kakaoAuthService.login(request);

        assertThat(response.user().getClass().getRecordComponents())
                .extracting(recordComponent -> recordComponent.getName())
                .doesNotContain("providerUserId");
    }

    private static User persisted(User user) {
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-06-22T08:00:00Z"));
        ReflectionTestUtils.setField(user, "updatedAt", Instant.parse("2026-06-22T08:00:00Z"));
        return user;
    }
}
