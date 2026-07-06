package com.runvas.backend.community.dto;

import jakarta.validation.constraints.Size;
import java.util.Set;

// docs/api-contract.md PATCH /posts/{id} — 전송한 필드만 수정, tags/attachedCourseId는 전체 교체.
// UpdateCourseRequest와 동일하게 null과 생략을 구분하지 않는다 (기존 Course 모듈 관례).
public record UpdatePostRequest(
		@Size(min = 1, max = 80) String title,
		@Size(min = 1, max = 5000) String body,
		String attachedCourseId,
		@Size(max = 10) Set<String> tags) {
}
