package com.runvas.auth.service;

import com.runvas.auth.dto.AppleLoginRequest;
import com.runvas.auth.dto.AuthResponse;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import com.runvas.user.dto.UserResponse;
import com.runvas.user.repository.UserRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class AppleAuthService {

    private static final Logger log = LoggerFactory.getLogger(AppleAuthService.class);

    private final AppleAuthClient appleAuthClient;
    private final AppleTokenExchangeClient appleTokenExchangeClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public AppleAuthService(
            AppleAuthClient appleAuthClient,
            AppleTokenExchangeClient appleTokenExchangeClient,
            UserRepository userRepository,
            JwtProvider jwtProvider
    ) {
        this.appleAuthClient = appleAuthClient;
        this.appleTokenExchangeClient = appleTokenExchangeClient;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
    }

    public AuthResponse login(AppleLoginRequest request) {
        if (!AuthProvider.APPLE.name().equals(request.provider())) {
            throw new RunvasException(ErrorCode.VALIDATION_ERROR, "provider must be APPLE");
        }

        AppleUserInfo appleUserInfo = appleAuthClient.verifyIdentityToken(request.identityToken());

        Optional<User> existingUser = userRepository.findByProviderAndProviderUserId(
                AuthProvider.APPLE,
                appleUserInfo.providerUserId()
        );
        existingUser.ifPresent(this::restoreIfWithdrawn);
        LoginResult loginResult = existingUser
                .map(user -> new LoginResult(user, false))
                .orElseGet(() -> createOrFindRacedUser(appleUserInfo, request.nickname()));

        applyRefreshTokenIfExchangeSucceeds(loginResult.user(), request.authorizationCode());

        String accessToken = jwtProvider.createAccessToken(loginResult.user().getId());
        return new AuthResponse(accessToken, UserResponse.from(loginResult.user()), loginResult.isNewUser());
    }

    private void applyRefreshTokenIfExchangeSucceeds(User user, String authorizationCode) {
        try {
            String refreshToken = appleTokenExchangeClient.exchangeForRefreshToken(authorizationCode);
            user.applyAppleRefreshToken(refreshToken);
            userRepository.save(user);
        } catch (Exception exception) {
            log.warn("Apple refresh token exchange failed for user {}, proceeding without storing it",
                    user.getId(), exception);
        }
    }

    private void restoreIfWithdrawn(User user) {
        if (user.isDeleted()) {
            user.restore();
            userRepository.save(user);
        }
    }

    private LoginResult createOrFindRacedUser(AppleUserInfo appleUserInfo, String nickname) {
        try {
            User user = userRepository.saveAndFlush(User.createAppleUser(
                    appleUserInfo.providerUserId(),
                    appleUserInfo.email(),
                    nickname
            ));
            return new LoginResult(user, true);
        } catch (DataIntegrityViolationException exception) {
            return userRepository.findByProviderAndProviderUserId(
                    AuthProvider.APPLE,
                    appleUserInfo.providerUserId()
            ).map(user -> new LoginResult(user, false))
                    .orElseThrow(() -> exception);
        }
    }

    private record LoginResult(User user, boolean isNewUser) {
    }
}
