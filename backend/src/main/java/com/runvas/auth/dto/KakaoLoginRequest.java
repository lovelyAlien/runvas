package com.runvas.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record KakaoLoginRequest(
        @NotBlank String provider,
        @NotBlank String authorizationCode,
        @NotBlank String redirectUri,
        @NotNull Instant termsAgreedAt
) {
}
