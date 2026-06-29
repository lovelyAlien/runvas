package com.runvas.backend.auth;

import com.runvas.backend.auth.dto.AuthResponse;
import com.runvas.backend.auth.dto.KakaoLoginRequest;
import com.runvas.backend.auth.kakao.KakaoOAuthClient;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.user.User;
import com.runvas.backend.user.UserRepository;
import com.runvas.backend.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final String PROVIDER_KAKAO = "KAKAO";

	private final KakaoOAuthClient kakaoOAuthClient;
	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;

	@Transactional
	public AuthResponse loginWithKakao(KakaoLoginRequest request) {
		if (!PROVIDER_KAKAO.equals(request.provider())) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 provider 입니다");
		}

		var kakaoUser = kakaoOAuthClient.exchangeAndFetchUser(request.authorizationCode(), request.redirectUri());

		var existing = userRepository.findByProviderAndProviderUserId(PROVIDER_KAKAO, kakaoUser.providerUserId());
		boolean isNewUser = existing.isEmpty();
		User user = existing.orElseGet(() -> userRepository.save(
				new User(kakaoUser.email(), PROVIDER_KAKAO, kakaoUser.providerUserId(), generateDefaultNickname())));

		String accessToken = jwtTokenProvider.createToken(user.getId());
		return new AuthResponse(accessToken, UserResponse.from(user), isNewUser);
	}

	// 카카오 기본 동의 항목에는 닉네임이 없어, 가입 시 임시 닉네임을 부여한다.
	// 사용자는 PATCH /me로 언제든 바꿀 수 있다.
	private String generateDefaultNickname() {
		return "Runner" + (int) (Math.random() * 1_000_000);
	}
}
