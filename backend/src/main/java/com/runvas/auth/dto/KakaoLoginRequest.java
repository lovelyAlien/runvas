package com.runvas.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank String provider,
        @NotBlank String authorizationCode,
        @NotBlank String redirectUri
) {
}
