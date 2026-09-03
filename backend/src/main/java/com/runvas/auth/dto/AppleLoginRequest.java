package com.runvas.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AppleLoginRequest(
        @NotBlank String provider,
        @NotBlank String identityToken,
        String nickname,
        @NotNull Instant termsAgreedAt
) {
}
