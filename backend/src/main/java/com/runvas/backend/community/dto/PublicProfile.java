package com.runvas.backend.community.dto;

import com.runvas.user.domain.User;

// docs/data-model.md PublicProfile과 1:1 — 커뮤니티 응답에서 User 전체 대신 이 필드만 노출한다.
public record PublicProfile(String id, String nickname, String profileImageUrl, String bio) {

	private static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

	public static PublicProfile from(User user) {
		return new PublicProfile(
				"user_" + user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getBio());
	}

	// docs/data-model.md "탈퇴한 사용자 표시" — 작성자 계정이 하드 삭제된 뒤에도 콘텐츠는 남기고
	// 작성자 표시만 고정 문구로 대체한다.
	public static PublicProfile withdrawn(String authorId) {
		return new PublicProfile(authorId, WITHDRAWN_NICKNAME, null, null);
	}
}
