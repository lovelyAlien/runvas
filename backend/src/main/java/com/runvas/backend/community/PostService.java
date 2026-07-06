package com.runvas.backend.community;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.common.PageInfo;
import com.runvas.backend.community.dto.CreatePostRequest;
import com.runvas.backend.community.dto.PostResponse;
import com.runvas.backend.community.dto.UpdatePostRequest;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import com.runvas.user.domain.User;
import com.runvas.user.dto.PublicProfileResponse;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

	private static final int DEFAULT_LIMIT = 20;
	private static final int MAX_LIMIT = 50;

	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final CourseRepository courseRepository;
	private final LikeRepository likeRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public PostResponse create(CreatePostRequest request) {
		String authorId = currentUserProvider.requireUserId();
		validateAttachedCourse(request.attachedCourseId());

		Post post = new Post(
				authorId,
				request.title(),
				request.body(),
				request.attachedCourseId(),
				request.tags() == null ? Set.of() : request.tags());

		postRepository.save(post);
		return toResponse(post, false);
	}

	@Transactional(readOnly = true)
	public PostResponse getById(String postId) {
		Post post = findPostOrThrow(postId);
		String currentUserId = currentUserProvider.currentUserIdOrNull();
		return toResponse(post, isLikedByCurrentUser(post.getId(), currentUserId));
	}

	@Transactional(readOnly = true)
	public ListResult list(String attachedCourseId, String q, String tag, String sort, Integer limit) {
		int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
		if (limit != null && (limit < 1 || limit > MAX_LIMIT)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "limit must be between 1 and " + MAX_LIMIT);
		}
		if (sort != null && !sort.equals("createdAtDesc") && !sort.equals("popularDesc")) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "unsupported sort: " + sort);
		}

		String currentUserId = currentUserProvider.currentUserIdOrNull();

		List<Post> posts = postRepository.findAllByOrderByCreatedAtDesc().stream()
				.filter(post -> attachedCourseId == null || attachedCourseId.equals(post.getAttachedCourseId()))
				.filter(post -> q == null || post.getTitle().contains(q) || post.getBody().contains(q))
				.filter(post -> tag == null || post.getTags().contains(tag))
				.toList();

		if ("popularDesc".equals(sort)) {
			posts = posts.stream()
					.sorted(Comparator.comparing(Post::getLikeCount).reversed())
					.toList();
		}

		List<PostResponse> responses = posts.stream()
				.limit(effectiveLimit)
				.map(post -> toResponse(post, isLikedByCurrentUser(post.getId(), currentUserId)))
				.toList();

		return new ListResult(responses, new PageInfo(null));
	}

	@Transactional
	public PostResponse update(String postId, UpdatePostRequest request) {
		Post post = findPostOrThrow(postId);
		requireAuthor(post);

		if (request.title() != null) post.setTitle(request.title());
		if (request.body() != null) post.setBody(request.body());
		if (request.tags() != null) post.replaceTags(request.tags());
		if (request.attachedCourseId() != null) {
			validateAttachedCourse(request.attachedCourseId());
			post.setAttachedCourseId(request.attachedCourseId());
		}

		post.setUpdatedAt(Instant.now());
		return toResponse(post, isLikedByCurrentUser(post.getId(), post.getAuthorId()));
	}

	@Transactional
	public void delete(String postId) {
		Post post = findPostOrThrow(postId);
		requireAuthor(post);
		postRepository.delete(post);
	}

	private void validateAttachedCourse(String attachedCourseId) {
		if (attachedCourseId == null) return;
		Course course = courseRepository.findById(attachedCourseId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "첨부 코스가 없습니다"));
		if (course.getVisibility() != CourseVisibility.PUBLIC) {
			throw new ApiException(ErrorCode.NOT_FOUND, "첨부 코스가 없습니다");
		}
	}

	private Post findPostOrThrow(String postId) {
		return postRepository.findById(postId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "게시글이 없습니다"));
	}

	private void requireAuthor(Post post) {
		String currentUserId = currentUserProvider.requireUserId();
		if (!post.getAuthorId().equals(currentUserId)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "작성자가 아닙니다");
		}
	}

	private boolean isLikedByCurrentUser(String postId, String currentUserId) {
		if (currentUserId == null) return false;
		return likeRepository.existsById(new Like.LikeId(currentUserId, LikeTargetType.POST, postId));
	}

	private PostResponse toResponse(Post post, boolean likedByMe) {
		User author = userRepository.findById(UUID.fromString(post.getAuthorId()))
				.orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "작성자를 찾을 수 없습니다"));
		return PostResponse.from(post, PublicProfileResponse.from(author), likedByMe);
	}

	public record ListResult(List<PostResponse> posts, PageInfo pageInfo) {
	}
}
