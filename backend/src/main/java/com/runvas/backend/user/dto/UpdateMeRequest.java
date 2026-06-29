package com.runvas.backend.user.dto;

import jakarta.validation.constraints.Size;

// docs/data-model.md 커뮤니티 제한값: 닉네임 2-30자, 소개 0-160자.
public record UpdateMeRequest(
		@Size(min = 2, max = 30) String nickname,
		String profileImageUrl,
		@Size(max = 160) String bio) {
}
