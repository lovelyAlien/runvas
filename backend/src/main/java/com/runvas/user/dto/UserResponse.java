package com.runvas.user.dto;

import com.runvas.user.domain.User;

public record UserResponse(
        String id,
        String email,
        String provider,
        String nickname,
        String profileImageUrl,
        String bio,
        int runningPaceSecPerKm,
        String createdAt,
        String updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                "user_" + user.getId(),
                user.getEmail(),
                user.getProvider().name(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getBio(),
                user.getRunningPaceSecPerKm(),
                user.getCreatedAt().toString(),
                user.getUpdatedAt().toString()
        );
    }
}
