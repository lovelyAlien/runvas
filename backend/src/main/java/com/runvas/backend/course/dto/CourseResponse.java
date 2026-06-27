package com.runvas.backend.course.dto;

import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.RoutePoint;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseVisibility;
import java.time.Instant;
import java.util.List;
import java.util.Set;

// docs/api-contract.md POST/GET /courses/{id} 응답의 course 객체 — path 포함.
public record CourseResponse(
		String id,
		String authorId,
		String title,
		String description,
		List<RoutePoint> path,
		Integer distanceMeters,
		Integer estimatedDurationSeconds,
		GeoBounds bounds,
		CourseVisibility visibility,
		Set<String> tags,
		Integer likeCount,
		boolean likedByMe,
		Instant createdAt,
		Instant updatedAt) {

	public static CourseResponse from(Course course, boolean likedByMe) {
		return new CourseResponse(
				course.getId(),
				course.getAuthorId(),
				course.getTitle(),
				course.getDescription(),
				course.getPath(),
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
