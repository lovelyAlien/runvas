package com.runvas.auth.controller;

import com.runvas.auth.dto.AuthResponse;
import com.runvas.auth.dto.KakaoLoginRequest;
import com.runvas.auth.service.AuthLogoutService;
import com.runvas.auth.service.KakaoAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final KakaoAuthService kakaoAuthService;
    private final AuthLogoutService authLogoutService;

    public AuthController(KakaoAuthService kakaoAuthService, AuthLogoutService authLogoutService) {
        this.kakaoAuthService = kakaoAuthService;
        this.authLogoutService = authLogoutService;
    }

    @PostMapping("/kakao")
    AuthResponse kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return kakaoAuthService.login(request);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        authLogoutService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
