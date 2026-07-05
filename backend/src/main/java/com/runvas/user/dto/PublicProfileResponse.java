package com.runvas.user.dto;

import com.runvas.user.domain.User;

// docs/data-model.md PublicProfile과 1:1. Post/Comment 작성자 정보에 쓴다.
// id는 UserResponse.from()과 동일하게 "user_" + UUID 포맷으로 통일한다.
public record PublicProfileResponse(String id, String nickname, String profileImageUrl, String bio) {
	public static PublicProfileResponse from(User user) {
		return new PublicProfileResponse(
				"user_" + user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getBio());
	}
}
