package com.runvas.backend.community.dto;

import com.runvas.backend.community.Comment;
import com.runvas.user.dto.PublicProfileResponse;
import java.time.Instant;

// docs/api-contract.md GET/POST /posts/{postId}/comments의 comment 객체.
public record CommentResponse(
		String id,
		String postId,
		PublicProfileResponse author,
		String body,
		Instant createdAt,
		Instant updatedAt) {

	public static CommentResponse from(Comment comment, PublicProfileResponse author) {
		return new CommentResponse(
				comment.getId(),
				comment.getPostId(),
				author,
				comment.getBody(),
				comment.getCreatedAt(),
				comment.getUpdatedAt());
	}
}
