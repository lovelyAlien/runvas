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
				course.getTags(),
				course.getLikeCount(),
				likedByMe,
				course.getCreatedAt(),
				course.getUpdatedAt());
	}
}
