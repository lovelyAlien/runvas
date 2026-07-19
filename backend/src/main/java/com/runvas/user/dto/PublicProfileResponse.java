package com.runvas.user.dto;

import com.runvas.user.domain.User;

// docs/data-model.md PublicProfile과 1:1. Post/Comment 작성자 정보에 쓴다.
// id는 UserResponse.from()과 동일하게 "user_" + UUID 포맷으로 통일한다.
public record PublicProfileResponse(String id, String nickname, String profileImageUrl, String bio) {

	private static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

	public static PublicProfileResponse from(User user) {
		return new PublicProfileResponse(
				"user_" + user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getBio());
	}

	// docs/data-model.md "탈퇴한 사용자 표시" — 작성자 계정이 하드 삭제된 뒤에도 콘텐츠는 남기고
	// 작성자 표시만 고정 문구로 대체한다.
	public static PublicProfileResponse withdrawn(String authorId) {
		return new PublicProfileResponse(authorId, WITHDRAWN_NICKNAME, null, null);
	}
}
