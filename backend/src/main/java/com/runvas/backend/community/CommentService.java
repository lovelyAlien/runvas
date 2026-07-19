package com.runvas.backend.community;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.common.PageInfo;
import com.runvas.backend.community.dto.CommentResponse;
import com.runvas.backend.community.dto.CreateCommentRequest;
import com.runvas.backend.community.dto.UpdateCommentRequest;
import com.runvas.user.dto.PublicProfileResponse;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

	private static final int DEFAULT_LIMIT = 20;
	private static final int MAX_LIMIT = 50;

	private final CommentRepository commentRepository;
	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public CommentResponse create(String postId, CreateCommentRequest request) {
		String authorId = currentUserProvider.requireUserId();
		Post post = findPostOrThrow(postId);

		Comment comment = new Comment(postId, authorId, request.body());
		commentRepository.save(comment);
		post.incrementCommentCount();

		return toResponse(comment);
	}

	@Transactional(readOnly = true)
	public ListResult list(String postId, Integer limit) {
		findPostOrThrow(postId);
		int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
		if (limit != null && (limit < 1 || limit > MAX_LIMIT)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "limit must be between 1 and " + MAX_LIMIT);
		}

		List<CommentResponse> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
				.limit(effectiveLimit)
				.map(this::toResponse)
				.toList();

		return new ListResult(comments, new PageInfo(null));
	}

	@Transactional
	public CommentResponse update(String commentId, UpdateCommentRequest request) {
		Comment comment = findCommentOrThrow(commentId);
		requireAuthor(comment);

		comment.setBody(request.body());
		comment.setUpdatedAt(Instant.now());
		return toResponse(comment);
	}

	@Transactional
	public void delete(String commentId) {
		Comment comment = findCommentOrThrow(commentId);
		requireAuthor(comment);

		commentRepository.delete(comment);
		postRepository.findById(comment.getPostId()).ifPresent(Post::decrementCommentCount);
	}

	private Post findPostOrThrow(String postId) {
		return postRepository.findById(postId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "게시글이 없습니다"));
	}

	private Comment findCommentOrThrow(String commentId) {
		return commentRepository.findById(commentId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "댓글이 없습니다"));
	}

	private void requireAuthor(Comment comment) {
		String currentUserId = currentUserProvider.requireUserId();
		if (!comment.getAuthorId().equals(currentUserId)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "작성자가 아닙니다");
		}
	}

	private CommentResponse toResponse(Comment comment) {
		PublicProfileResponse author = userRepository.findById(UUID.fromString(comment.getAuthorId()))
				.map(PublicProfileResponse::from)
				.orElseGet(() -> PublicProfileResponse.withdrawn(comment.getAuthorId()));
		return CommentResponse.from(comment, author);
	}

	public record ListResult(List<CommentResponse> comments, PageInfo pageInfo) {
	}
}
