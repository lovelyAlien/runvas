package com.runvas.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
		@NotBlank String provider, @NotBlank String authorizationCode, String redirectUri) {
}
