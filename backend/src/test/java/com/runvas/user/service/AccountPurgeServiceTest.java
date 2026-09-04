package com.runvas.user.service;

import com.runvas.auth.service.AppleRevokeClient;
import com.runvas.auth.service.KakaoUnlinkClient;
import com.runvas.backend.community.BookmarkRepository;
import com.runvas.backend.community.LikeService;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountPurgeServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LikeService likeService = mock(LikeService.class);
    private final BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
    private final KakaoUnlinkClient kakaoUnlinkClient = mock(KakaoUnlinkClient.class);
    private final AppleRevokeClient appleRevokeClient = mock(AppleRevokeClient.class);
    private final AccountPurgeService accountPurgeService = new AccountPurgeService(
            userRepository, likeService, bookmarkRepository, kakaoUnlinkClient, appleRevokeClient);

    private static User kakaoUser(String providerUserId) {
        User user = User.createKakaoUser(providerUserId, null, "탈퇴예정", null);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.markWithdrawn();
        return user;
    }

    private static User appleUser(String providerUserId, String appleRefreshToken) {
        User user = User.createAppleUser(providerUserId, null, "탈퇴예정");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        if (appleRefreshToken != null) {
            user.applyAppleRefreshToken(appleRefreshToken);
        }
        user.markWithdrawn();
        return user;
    }

    @Test
    void purgesExpiredKakaoUserAfterUnlinkingAndDeletingLikesAndBookmarks() {
        User expired = kakaoUser("kakao-expired");
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));

        accountPurgeService.purgeExpiredAccounts();

        verify(kakaoUnlinkClient).unlink("kakao-expired");
        verify(likeService).unlikeAllByUser(expired.getId().toString());
        verify(bookmarkRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(userRepository).delete(expired);
    }

    @Test
    void skipsUnlinkForDevProvider() {
        User devUser = User.createDevUser("dev-nickname");
        ReflectionTestUtils.setField(devUser, "id", UUID.randomUUID());
        devUser.markWithdrawn();
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(devUser));

        accountPurgeService.purgeExpiredAccounts();

        verify(kakaoUnlinkClient, never()).unlink(anyString());
        verify(appleRevokeClient, never()).revoke(anyString());
        verify(userRepository).delete(devUser);
    }

    @Test
    void continuesDeletionWhenUnlinkFails() {
        User expired = kakaoUser("kakao-unlink-fails");
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));
        doThrow(new IllegalStateException("kakao down")).when(kakaoUnlinkClient).unlink("kakao-unlink-fails");

        accountPurgeService.purgeExpiredAccounts();

        verify(likeService).unlikeAllByUser(expired.getId().toString());
        verify(bookmarkRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(userRepository).delete(expired);
    }

    @Test
    void purgesExpiredAppleUserAfterRevokingAndDeletingLikesAndBookmarks() {
        User expired = appleUser("apple-expired", "apple-refresh-token-value");
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));

        accountPurgeService.purgeExpiredAccounts();

        verify(appleRevokeClient).revoke("apple-refresh-token-value");
        verify(likeService).unlikeAllByUser(expired.getId().toString());
        verify(bookmarkRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(userRepository).delete(expired);
    }

    @Test
    void skipsRevokeWhenAppleRefreshTokenMissing() {
        User expired = appleUser("apple-no-token", null);
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));

        accountPurgeService.purgeExpiredAccounts();

        verify(appleRevokeClient, never()).revoke(anyString());
        verify(userRepository).delete(expired);
    }

    @Test
    void continuesDeletionWhenAppleRevokeFails() {
        User expired = appleUser("apple-revoke-fails", "apple-refresh-token-value");
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));
        doThrow(new IllegalStateException("apple down")).when(appleRevokeClient).revoke("apple-refresh-token-value");

        accountPurgeService.purgeExpiredAccounts();

        verify(likeService).unlikeAllByUser(expired.getId().toString());
        verify(bookmarkRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(userRepository).delete(expired);
    }
}
