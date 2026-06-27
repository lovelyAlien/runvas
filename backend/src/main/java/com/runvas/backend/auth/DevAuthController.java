package com.runvas.backend.auth;

import com.runvas.backend.auth.dto.AuthResponse;
import com.runvas.backend.auth.dto.DevLoginRequest;
import com.runvas.backend.user.User;
import com.runvas.backend.user.UserRepository;
import com.runvas.backend.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// docs/api-contract.md에 없는 개발용 엔드포인트. 카카오 앱 키/SDK 연동 전까지 모바일이 실제
// JWT를 받아 인증이 필요한 API(POST /courses 등)를 테스트할 수 있게 한다.
// provider="DEV"로 저장해 실제 카카오 사용자와 절대 섞이지 않는다.
// 카카오 로그인이 실제로 연동되면 이 컨트롤러와 mobile의 AuthContext.mockLogin 호출부를
// 함께 제거한다 (mobile/docs/implementations/entry-screen-auth-gating.md TODO 참고).
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class DevAuthController {

	private static final String PROVIDER_DEV = "DEV";

	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;

	@PostMapping("/dev-login")
	public AuthResponse devLogin(@RequestBody DevLoginRequest request) {
		String nickname = (request.nickname() == null || request.nickname().isBlank())
				? "DevRunner" + (int) (Math.random() * 1_000_000)
				: request.nickname();

		var existing = userRepository.findByProviderAndProviderUserId(PROVIDER_DEV, nickname);
		boolean isNewUser = existing.isEmpty();
		User user = existing.orElseGet(() -> userRepository.save(new User(null, PROVIDER_DEV, nickname, nickname)));

		String accessToken = jwtTokenProvider.createToken(user.getId());
		return new AuthResponse(accessToken, UserResponse.from(user), isNewUser);
	}
}
