package com.runvas.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
        @NotBlank String provider,
        @NotBlank String identityToken,
        String nickname
) {
}
