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
}
