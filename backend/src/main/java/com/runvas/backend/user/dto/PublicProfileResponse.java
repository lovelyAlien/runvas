package com.runvas.backend.user.dto;

import com.runvas.backend.user.User;

public record PublicProfileResponse(String id, String nickname, String profileImageUrl, String bio) {

	public static PublicProfileResponse from(User user) {
		return new PublicProfileResponse(
				user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getBio());
	}
}
