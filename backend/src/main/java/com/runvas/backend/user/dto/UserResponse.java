package com.runvas.backend.user.dto;

import com.runvas.backend.user.User;
import java.time.Instant;

// docs/api-contract.md User 객체 — providerUserId는 응답에 포함하지 않는다.
public record UserResponse(
		String id,
		String email,
		String provider,
		String nickname,
		String profileImageUrl,
		String bio,
		Instant createdAt,
		Instant updatedAt) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getEmail(),
				user.getProvider(),
				user.getNickname(),
				user.getProfileImageUrl(),
				user.getBio(),
				user.getCreatedAt(),
				user.getUpdatedAt());
	}
}
