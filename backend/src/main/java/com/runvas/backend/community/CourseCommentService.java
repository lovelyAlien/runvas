package com.runvas.backend.community;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.common.PageInfo;
import com.runvas.backend.community.dto.CourseCommentResponse;
import com.runvas.backend.community.dto.PublicProfile;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseCommentService {

	private static final int DEFAULT_LIMIT = 20;
	private static final int MAX_LIMIT = 50;
	private static final int MIN_BODY_LENGTH = 1;
	private static final int MAX_BODY_LENGTH = 1000;
	private static final int MAX_REPLIES = 200;

	private final CourseCommentRepository courseCommentRepository;
	private final CourseRepository courseRepository;
	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional(readOnly = true)
	public ListResult list(String courseId, Integer limit, String cursor) {
		Course course = findCourseOrThrow(courseId);
		String currentUserId = currentUserProvider.currentUserIdOrNull();
		requireReadableCourse(course, currentUserId);

		int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
		if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "limit must be between 1 and " + MAX_LIMIT);
		}

		Pageable pageable = PageRequest.of(0, effectiveLimit + 1);
		List<CourseComment> comments = (cursor == null || cursor.isBlank())
				? courseCommentRepository.findFirstPage(courseId, pageable)
				: courseCommentRepository.findNextPage(courseId, cursorCreatedAt(cursor), cursor, pageable);

		boolean hasMore = comments.size() > effectiveLimit;
		List<CourseComment> page = hasMore ? comments.subList(0, effectiveLimit) : comments;
		String nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

		Map<String, Long> replyCounts = replyCountsFor(page);
		List<CourseCommentResponse> responses =
				page.stream().map(comment -> toResponse(comment, replyCounts.getOrDefault(comment.getId(), 0L))).toList();
		return new ListResult(responses, new PageInfo(nextCursor));
	}

	@Transactional(readOnly = true)
	public List<CourseCommentResponse> listReplies(String courseId, String parentCommentId) {
		Course course = findCourseOrThrow(courseId);
		String currentUserId = currentUserProvider.currentUserIdOrNull();
		requireReadableCourse(course, currentUserId);
		findCommentOrThrow(courseId, parentCommentId);

		List<CourseComment> replies = courseCommentRepository.findByParentCommentIdOrderByCreatedAtAsc(
				parentCommentId, PageRequest.of(0, MAX_REPLIES));
		Map<String, Long> replyCounts = replyCountsFor(replies);
		return replies.stream()
				.map(reply -> toResponse(reply, replyCounts.getOrDefault(reply.getId(), 0L)))
				.toList();
	}

	@Transactional
	public CourseCommentResponse create(String courseId, String body, String parentCommentId) {
		String authorId = currentUserProvider.requireUserId();
		Course course = findCourseOrThrow(courseId);
		requirePublicCourse(course);
		validateBody(body);
		if (parentCommentId != null) {
			validateParentComment(courseId, parentCommentId);
		}

		CourseComment comment = new CourseComment(courseId, authorId, parentCommentId, body);
		courseCommentRepository.save(comment);
		return toResponse(comment, 0L);
	}

	@Transactional
	public CourseCommentResponse update(String courseId, String commentId, String body) {
		CourseComment comment = findCommentOrThrow(courseId, commentId);
		requireAuthor(comment);

		if (body != null) {
			validateBody(body);
			comment.setBody(body);
		}

		comment.setUpdatedAt(Instant.now());
		return toResponse(comment, countReplies(comment.getId()));
	}

	@Transactional
	public void delete(String courseId, String commentId) {
		CourseComment comment = courseCommentRepository.findById(commentId).orElse(null);
		if (comment == null || !comment.getCourseId().equals(courseId)) {
			return;
		}
		requireAuthor(comment);
		courseCommentRepository.delete(comment);
	}

	private void validateParentComment(String courseId, String parentCommentId) {
		CourseComment parent = courseCommentRepository
				.findById(parentCommentId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "부모 댓글이 없습니다"));
		if (!parent.getCourseId().equals(courseId)) {
			throw new ApiException(ErrorCode.NOT_FOUND, "부모 댓글이 없습니다");
		}
	}

	private long countReplies(String commentId) {
		return courseCommentRepository.countRepliesByParentCommentIds(List.of(commentId)).stream()
				.findFirst()
				.map(CourseCommentRepository.ReplyCountRow::getReplyCount)
				.orElse(0L);
	}

	private Map<String, Long> replyCountsFor(List<CourseComment> comments) {
		List<String> ids = comments.stream().map(CourseComment::getId).toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return courseCommentRepository.countRepliesByParentCommentIds(ids).stream()
				.collect(Collectors.toMap(
						CourseCommentRepository.ReplyCountRow::getParentCommentId,
						CourseCommentRepository.ReplyCountRow::getReplyCount));
	}

	private Instant cursorCreatedAt(String cursorCommentId) {
		return courseCommentRepository
				.findById(cursorCommentId)
				.map(CourseComment::getCreatedAt)
				.orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "유효하지 않은 cursor입니다"));
	}

	private void validateBody(String body) {
		if (body == null || body.length() < MIN_BODY_LENGTH || body.length() > MAX_BODY_LENGTH) {
			throw new ApiException(
					ErrorCode.VALIDATION_ERROR, "댓글 본문은 " + MIN_BODY_LENGTH + "-" + MAX_BODY_LENGTH + "자여야 합니다");
		}
	}

	private void requireReadableCourse(Course course, String currentUserId) {
		if (course.getVisibility() == CourseVisibility.PRIVATE
				&& !course.getAuthorId().equals(currentUserId)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "비공개 코스 작성자가 아닙니다");
		}
	}

	private void requirePublicCourse(Course course) {
		if (course.getVisibility() != CourseVisibility.PUBLIC) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "PUBLIC 코스에만 댓글을 작성할 수 있습니다");
		}
	}

	private void requireAuthor(CourseComment comment) {
		String currentUserId = currentUserProvider.requireUserId();
		if (!comment.getAuthorId().equals(currentUserId)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "작성자가 아닙니다");
		}
	}

	private Course findCourseOrThrow(String courseId) {
		return courseRepository.findById(courseId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "코스가 없습니다"));
	}

	private CourseComment findCommentOrThrow(String courseId, String commentId) {
		CourseComment comment = courseCommentRepository.findById(commentId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "댓글이 없습니다"));
		if (!comment.getCourseId().equals(courseId)) {
			throw new ApiException(ErrorCode.NOT_FOUND, "댓글이 없습니다");
		}
		return comment;
	}

	private CourseCommentResponse toResponse(CourseComment comment, long replyCount) {
		return CourseCommentResponse.from(comment, resolveAuthor(comment.getAuthorId()), replyCount);
	}

	private PublicProfile resolveAuthor(String authorId) {
		User author = userRepository
				.findById(UUID.fromString(authorId))
				.orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "작성자를 찾을 수 없습니다"));
		return PublicProfile.from(author);
	}

	public record ListResult(List<CourseCommentResponse> comments, PageInfo pageInfo) {
	}
}
