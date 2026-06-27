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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
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
        boolean isNewUser = existingUser.isEmpty();
        User user = existingUser.orElseGet(() -> userRepository.save(User.createKakaoUser(
                kakaoUserInfo.providerUserId(),
                kakaoUserInfo.email(),
                kakaoUserInfo.nickname(),
                kakaoUserInfo.profileImageUrl()
        )));

        String accessToken = jwtProvider.createAccessToken(user.getId());
        return new AuthResponse(accessToken, UserResponse.from(user), isNewUser);
    }
}
