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
}
