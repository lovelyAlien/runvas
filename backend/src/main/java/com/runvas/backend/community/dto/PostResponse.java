package com.runvas.backend.community.dto;

import com.runvas.backend.community.Post;
import com.runvas.user.dto.PublicProfileResponse;
import java.time.Instant;
import java.util.Set;

// docs/api-contract.md GET/POST /posts의 post 객체.
public record PostResponse(
		String id,
		PublicProfileResponse author,
		String title,
		String body,
		String attachedCourseId,
		Set<String> tags,
		Integer likeCount,
		boolean likedByMe,
		Integer commentCount,
		Instant createdAt,
		Instant updatedAt) {

	public static PostResponse from(Post post, PublicProfileResponse author, boolean likedByMe) {
		return new PostResponse(
				post.getId(),
				author,
				post.getTitle(),
				post.getBody(),
				post.getAttachedCourseId(),
				// tags는 지연 로딩 컬렉션 — 트랜잭션이 끝난 뒤(Jackson 직렬화 시점) 접근하면
				// LazyInitializationException이 나므로, 트랜잭션 안에서 즉시 복사해 둔다 (Course와 동일 이유).
				Set.copyOf(post.getTags()),
				post.getLikeCount(),
				likedByMe,
				post.getCommentCount(),
				post.getCreatedAt(),
				post.getUpdatedAt());
	}
}
