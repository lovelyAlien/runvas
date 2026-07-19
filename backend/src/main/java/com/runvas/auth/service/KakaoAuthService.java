package com.runvas.auth.service;

import com.runvas.auth.dto.AuthResponse;
import com.runvas.auth.dto.KakaoLoginRequest;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import com.runvas.user.dto.UserResponse;
import com.runvas.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class KakaoAuthService {

    private final KakaoAuthClient kakaoAuthClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public KakaoAuthService(KakaoAuthClient kakaoAuthClient, UserRepository userRepository, JwtProvider jwtProvider) {
        this.kakaoAuthClient = kakaoAuthClient;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
    }

    public AuthResponse login(KakaoLoginRequest request) {
        if (!AuthProvider.KAKAO.name().equals(request.provider())) {
            throw new RunvasException(ErrorCode.VALIDATION_ERROR, "provider must be KAKAO");
        }

        KakaoUserInfo kakaoUserInfo = kakaoAuthClient.fetchUserInfo(
                request.authorizationCode(),
                request.redirectUri()
        );

        Optional<User> existingUser = userRepository.findByProviderAndProviderUserId(
                AuthProvider.KAKAO,
                kakaoUserInfo.providerUserId()
        );
        existingUser.ifPresent(this::restoreIfWithdrawn);
        LoginResult loginResult = existingUser
                .map(user -> new LoginResult(user, false))
                .orElseGet(() -> createOrFindRacedUser(kakaoUserInfo));

        String accessToken = jwtProvider.createAccessToken(loginResult.user().getId());
        return new AuthResponse(accessToken, UserResponse.from(loginResult.user()), loginResult.isNewUser());
    }

    private void restoreIfWithdrawn(User user) {
        if (user.isDeleted()) {
            user.restore();
            userRepository.save(user);
        }
    }

    private LoginResult createOrFindRacedUser(KakaoUserInfo kakaoUserInfo) {
        try {
            User user = userRepository.saveAndFlush(User.createKakaoUser(
                    kakaoUserInfo.providerUserId(),
                    kakaoUserInfo.email(),
                    kakaoUserInfo.nickname(),
                    kakaoUserInfo.profileImageUrl()
            ));
            return new LoginResult(user, true);
        } catch (DataIntegrityViolationException exception) {
            return userRepository.findByProviderAndProviderUserId(
                    AuthProvider.KAKAO,
                    kakaoUserInfo.providerUserId()
            ).map(user -> new LoginResult(user, false))
                    .orElseThrow(() -> exception);
        }
    }

    private record LoginResult(User user, boolean isNewUser) {
    }
}
