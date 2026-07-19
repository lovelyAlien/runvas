package com.runvas.user.service;

import com.runvas.auth.service.KakaoUnlinkClient;
import com.runvas.backend.community.BookmarkRepository;
import com.runvas.backend.community.LikeService;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountPurgeService {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeService.class);
    private static final int GRACE_PERIOD_DAYS = 30;

    private final UserRepository userRepository;
    private final LikeService likeService;
    private final BookmarkRepository bookmarkRepository;
    private final KakaoUnlinkClient kakaoUnlinkClient;

    public AccountPurgeService(
            UserRepository userRepository,
            LikeService likeService,
            BookmarkRepository bookmarkRepository,
            KakaoUnlinkClient kakaoUnlinkClient
    ) {
        this.userRepository = userRepository;
        this.likeService = likeService;
        this.bookmarkRepository = bookmarkRepository;
        this.kakaoUnlinkClient = kakaoUnlinkClient;
    }

    @Transactional
    public void purgeExpiredAccounts() {
        Instant threshold = Instant.now().minus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
        List<User> expired = userRepository.findByDeletedAtLessThanEqual(threshold);
        for (User user : expired) {
            purgeOne(user);
        }
    }

    private void purgeOne(User user) {
        if (user.getProvider() == AuthProvider.KAKAO) {
            try {
                kakaoUnlinkClient.unlink(user.getProviderUserId());
            } catch (Exception exception) {
                log.warn("Kakao unlink failed for user {}, proceeding with deletion", user.getId(), exception);
            }
        }
        likeService.unlikeAllByUser(user.getId().toString());
        bookmarkRepository.deleteAllByIdUserId(user.getId().toString());
        userRepository.delete(user);
    }
}
