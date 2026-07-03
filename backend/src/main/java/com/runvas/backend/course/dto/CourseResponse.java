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
		List<RoutePoint> waypoints,
		Integer distanceMeters,
		Integer estimatedDurationSeconds,
		GeoBounds bounds,
		String startAddress,
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
				course.getWaypoints(),
				course.getDistanceMeters(),
				course.getEstimatedDurationSeconds(),
				course.getBounds(),
				course.getStartAddress(),
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
