package com.runvas.backend.community.dto;

import com.runvas.backend.community.CourseComment;
import java.time.Instant;

// docs/api-contract.md Course Comment APIs 응답의 단일 comment 객체 — docs/data-model.md CourseComment와 1:1.
public record CourseCommentResponse(
		String id,
		String courseId,
		String parentCommentId,
		PublicProfile author,
		String body,
		long replyCount,
		Instant createdAt,
		Instant updatedAt) {

	public static CourseCommentResponse from(CourseComment comment, PublicProfile author, long replyCount) {
		return new CourseCommentResponse(
				comment.getId(),
				comment.getCourseId(),
				comment.getParentCommentId(),
				author,
				comment.getBody(),
				replyCount,
				comment.getCreatedAt(),
				comment.getUpdatedAt());
	}
}
