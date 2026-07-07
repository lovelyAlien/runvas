package com.runvas.backend.community.dto;

import com.runvas.user.domain.User;

// docs/data-model.md PublicProfile과 1:1 — 커뮤니티 응답에서 User 전체 대신 이 필드만 노출한다.
public record PublicProfile(String id, String nickname, String profileImageUrl, String bio) {

	public static PublicProfile from(User user) {
		return new PublicProfile(
				"user_" + user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getBio());
	}
}
