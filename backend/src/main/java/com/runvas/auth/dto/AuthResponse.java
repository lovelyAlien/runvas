package com.runvas.auth.dto;

import com.runvas.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        UserResponse user,
        boolean isNewUser
) {
}
