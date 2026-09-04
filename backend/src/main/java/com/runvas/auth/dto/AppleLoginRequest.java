package com.runvas.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AppleLoginRequest(
        @NotBlank String provider,
        @NotBlank String identityToken,
        @NotBlank String authorizationCode,
        String nickname,
        @NotNull Instant termsAgreedAt
) {
}
