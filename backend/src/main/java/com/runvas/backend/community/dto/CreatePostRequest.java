package com.runvas.backend.community.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

// docs/api-contract.md POST /posts 요청 본문, docs/data-model.md 커뮤니티 제한값.
public record CreatePostRequest(
		@NotNull @Size(min = 1, max = 80) String title,
		@NotNull @Size(min = 1, max = 5000) String body,
		String attachedCourseId,
		@Size(max = 10) Set<String> tags) {
}
