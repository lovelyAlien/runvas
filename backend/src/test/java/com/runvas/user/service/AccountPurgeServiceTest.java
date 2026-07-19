package com.runvas.user.service;

import com.runvas.auth.service.KakaoUnlinkClient;
import com.runvas.backend.community.BookmarkRepository;
import com.runvas.backend.community.LikeRepository;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
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
    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
    private final KakaoUnlinkClient kakaoUnlinkClient = mock(KakaoUnlinkClient.class);
    private final AccountPurgeService accountPurgeService =
            new AccountPurgeService(userRepository, likeRepository, bookmarkRepository, kakaoUnlinkClient);

    private static User kakaoUser(String providerUserId) {
        User user = User.createKakaoUser(providerUserId, null, "탈퇴예정", null);
        ReflectionTestUtils.setField(user, "id", java.util.UUID.randomUUID());
        user.markWithdrawn();
        return user;
    }

    @Test
    void purgesExpiredKakaoUserAfterUnlinkingAndDeletingLikesAndBookmarks() {
        User expired = kakaoUser("kakao-expired");
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));

        accountPurgeService.purgeExpiredAccounts();

        verify(kakaoUnlinkClient).unlink("kakao-expired");
        verify(likeRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(bookmarkRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(userRepository).delete(expired);
    }

    @Test
    void skipsUnlinkForDevProvider() {
        User devUser = User.createDevUser("dev-nickname");
        ReflectionTestUtils.setField(devUser, "id", java.util.UUID.randomUUID());
        devUser.markWithdrawn();
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(devUser));

        accountPurgeService.purgeExpiredAccounts();

        verify(kakaoUnlinkClient, never()).unlink(anyString());
        verify(userRepository).delete(devUser);
    }

    @Test
    void continuesDeletionWhenUnlinkFails() {
        User expired = kakaoUser("kakao-unlink-fails");
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));
        doThrow(new IllegalStateException("kakao down")).when(kakaoUnlinkClient).unlink("kakao-unlink-fails");

        accountPurgeService.purgeExpiredAccounts();

        verify(likeRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(bookmarkRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(userRepository).delete(expired);
    }
}
