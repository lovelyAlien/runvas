package com.runvas.backend.auth.dto;

import com.runvas.backend.user.dto.UserResponse;

public record AuthResponse(String accessToken, UserResponse user, boolean isNewUser) {
}
