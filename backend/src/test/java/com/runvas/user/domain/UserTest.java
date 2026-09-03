package com.runvas.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void newUserIsNotDeleted() {
        User user = User.createKakaoUser("kakao-1", "runner@example.com", "Seoul Runner", null);

        assertThat(user.isDeleted()).isFalse();
        assertThat(user.getDeletedAt()).isNull();
    }

    @Test
    void markWithdrawnSetsDeletedAtToNow() {
        User user = User.createKakaoUser("kakao-1", "runner@example.com", "Seoul Runner", null);

        user.markWithdrawn();

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    void restoreClearsDeletedAt() {
        User user = User.createKakaoUser("kakao-1", "runner@example.com", "Seoul Runner", null);
        user.markWithdrawn();

        user.restore();

        assertThat(user.isDeleted()).isFalse();
        assertThat(user.getDeletedAt()).isNull();
    }

    @Test
    void createAppleUser_닉네임이_없으면_기본_닉네임을_사용한다() {
        User user = User.createAppleUser("apple-sub-123", "runner@example.com", null);

        assertThat(user.getProvider()).isEqualTo(AuthProvider.APPLE);
        assertThat(user.getProviderUserId()).isEqualTo("apple-sub-123");
        assertThat(user.getEmail()).isEqualTo("runner@example.com");
        assertThat(user.getNickname()).isEqualTo("Runvas Runner");
        assertThat(user.getProfileImageUrl()).isNull();
    }

    @Test
    void createAppleUser_닉네임이_있으면_그대로_사용한다() {
        User user = User.createAppleUser("apple-sub-456", null, "Jeju Runner");

        assertThat(user.getNickname()).isEqualTo("Jeju Runner");
        assertThat(user.getEmail()).isNull();
    }

    @Test
    void ban_호출하면_isBanned가_true가_된다() {
        User user = User.createKakaoUser("kakao-1", "runner@example.com", "Runner", null);

        assertThat(user.isBanned()).isFalse();
        user.ban();
        assertThat(user.isBanned()).isTrue();
        assertThat(user.getBannedAt()).isNotNull();
    }

    @Test
    void agreeToTerms_최초_한_번만_저장되고_이후_호출은_무시한다() {
        User user = User.createKakaoUser("kakao-2", "runner@example.com", "Runner", null);
        java.time.Instant first = java.time.Instant.parse("2026-09-01T00:00:00Z");
        java.time.Instant second = java.time.Instant.parse("2026-09-02T00:00:00Z");

        user.agreeToTerms(first);
        user.agreeToTerms(second);

        assertThat(user.getTermsAgreedAt()).isEqualTo(first);
    }
}
