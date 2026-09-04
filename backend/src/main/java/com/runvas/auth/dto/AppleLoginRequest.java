package com.runvas.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
        @NotBlank String provider,
        @NotBlank String identityToken,
        @NotBlank String authorizationCode,
        String nickname
) {
}
