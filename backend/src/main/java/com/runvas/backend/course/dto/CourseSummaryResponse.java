package com.runvas.backend.course.dto;

import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseVisibility;
import java.time.Instant;
import java.util.Set;

// docs/api-contract.md GET /courses 목록 응답 — path는 포함하지 않는다.
public record CourseSummaryResponse(
		String id,
		String authorId,
		String title,
		String description,
		Integer distanceMeters,
		Integer estimatedDurationSeconds,
		GeoBounds bounds,
		CourseVisibility visibility,
		Set<String> tags,
		Integer likeCount,
		boolean likedByMe,
		Instant createdAt,
		Instant updatedAt) {

	public static CourseSummaryResponse from(Course course, boolean likedByMe) {
		return new CourseSummaryResponse(
				course.getId(),
				course.getAuthorId(),
				course.getTitle(),
				course.getDescription(),
				course.getDistanceMeters(),
				course.getEstimatedDurationSeconds(),
				course.getBounds(),
				course.getVisibility(),
				// tags는 지연 로딩 컬렉션 — 트랜잭션이 끝난 뒤(Jackson 직렬화 시점) 접근하면
				// LazyInitializationException이 나므로, 트랜잭션 안에서 즉시 복사해 둔다.
				Set.copyOf(course.getTags()),
				course.getLikeCount(),
				likedByMe,
				course.getCreatedAt(),
				course.getUpdatedAt());
	}
}
