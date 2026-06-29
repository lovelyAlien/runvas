package com.runvas.backend.auth;

import com.runvas.auth.dto.AuthResponse;
import com.runvas.auth.service.JwtProvider;
import com.runvas.backend.auth.dto.DevLoginRequest;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import com.runvas.user.dto.UserResponse;
import com.runvas.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// docs/api-contract.md에 없는 개발용 엔드포인트. 카카오 앱 키/SDK 연동 전까지 모바일이 실제
// JWT를 받아 인증이 필요한 API(POST /courses 등)를 테스트할 수 있게 한다.
// provider=DEV로 저장해 실제 카카오 사용자와 절대 섞이지 않는다.
// 카카오 로그인이 실제로 연동되면 이 컨트롤러와 mobile의 AuthContext.mockLogin 호출부를
// 함께 제거한다 (mobile/docs/implementations/entry-screen-auth-gating.md TODO 참고).
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class DevAuthController {

	private final UserRepository userRepository;
	private final JwtProvider jwtProvider;

	@PostMapping("/dev-login")
	public AuthResponse devLogin(@RequestBody DevLoginRequest request) {
		String nickname = (request.nickname() == null || request.nickname().isBlank())
				? "DevRunner" + (int) (Math.random() * 1_000_000)
				: request.nickname();

		var existing = userRepository.findByProviderAndProviderUserId(AuthProvider.DEV, nickname);
		boolean isNewUser = existing.isEmpty();
		User user = existing.orElseGet(() -> userRepository.save(User.createDevUser(nickname)));

		String accessToken = jwtProvider.createAccessToken(user.getId());
		return new AuthResponse(accessToken, UserResponse.from(user), isNewUser);
	}
}
