package com.runvas.backend.auth;

import com.runvas.backend.auth.dto.AuthResponse;
import com.runvas.backend.auth.dto.KakaoLoginRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/kakao")
	public ResponseEntity<AuthResponse> loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
		return ResponseEntity.ok(authService.loginWithKakao(request));
	}
}
