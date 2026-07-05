package com.runvas.backend.course.dto;

import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.community.Bookmark;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseVisibility;
import java.time.Instant;
import java.util.Set;

// docs/api-contract.md GET /me/bookmarked-courses 응답 항목 — CourseSummary 필드 + bookmarkedAt.
public record BookmarkedCourseSummaryResponse(
		String id,
		String authorId,
		String title,
		String description,
		Integer distanceMeters,
		Integer estimatedDurationSeconds,
		GeoBounds bounds,
		String startAddress,
		CourseVisibility visibility,
		Set<String> tags,
		Integer likeCount,
		boolean likedByMe,
		Instant createdAt,
		Instant updatedAt,
		Instant bookmarkedAt) {

	public static BookmarkedCourseSummaryResponse from(Course course, boolean likedByMe, Bookmark bookmark) {
		return new BookmarkedCourseSummaryResponse(
				course.getId(),
				course.getAuthorId(),
				course.getTitle(),
				course.getDescription(),
				course.getDistanceMeters(),
				course.getEstimatedDurationSeconds(),
				course.getBounds(),
				course.getStartAddress(),
				course.getVisibility(),
				Set.copyOf(course.getTags()),
				course.getLikeCount(),
				likedByMe,
				course.getCreatedAt(),
				course.getUpdatedAt(),
				bookmark.getCreatedAt());
	}
}
