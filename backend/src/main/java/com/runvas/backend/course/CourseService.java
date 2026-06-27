package com.runvas.backend.course;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.common.PageInfo;
import com.runvas.backend.community.Like;
import com.runvas.backend.community.LikeRepository;
import com.runvas.backend.community.LikeTargetType;
import com.runvas.backend.course.dto.CourseResponse;
import com.runvas.backend.course.dto.CourseSummaryResponse;
import com.runvas.backend.course.dto.CreateCourseRequest;
import com.runvas.backend.course.dto.UpdateCourseRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseService {

	private static final int DEFAULT_LIMIT = 20;
	private static final int MAX_LIMIT = 50;

	private final CourseRepository courseRepository;
	private final LikeRepository likeRepository;
	private final CourseValidator courseValidator;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public CourseResponse create(CreateCourseRequest request) {
		String authorId = currentUserProvider.requireUserId();
		courseValidator.validate(request.path(), request.distanceMeters(), request.bounds());

		Course course = new Course(
				authorId,
				request.title(),
				request.description(),
				request.path(),
				request.distanceMeters(),
				request.estimatedDurationSeconds(),
				request.bounds(),
				request.visibility(),
				request.tags() == null ? Set.of() : request.tags());

		courseRepository.save(course);
		return CourseResponse.from(course, false);
	}

	public CourseResponse getById(String courseId) {
		Course course = findCourseOrThrow(courseId);
		String currentUserId = currentUserProvider.currentUserIdOrNull();

		if (course.getVisibility() == CourseVisibility.PRIVATE) {
			if (currentUserId == null) {
				throw new ApiException(ErrorCode.UNAUTHORIZED, "비공개 코스 조회에 인증이 필요합니다");
			}
			if (!course.getAuthorId().equals(currentUserId)) {
				throw new ApiException(ErrorCode.FORBIDDEN, "비공개 코스 작성자가 아닙니다");
			}
		}

		return CourseResponse.from(course, isLikedByCurrentUser(course.getId(), currentUserId));
	}

	public ListResult list(double swLat, double swLng, double neLat, double neLng, Integer limit, String q, String tag) {
		int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
		if (limit != null && limit > MAX_LIMIT) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "limit must be at most " + MAX_LIMIT);
		}

		String currentUserId = currentUserProvider.currentUserIdOrNull();

		List<CourseSummaryResponse> courses = courseRepository
				.findPublicCoursesWithinBounds(swLat, swLng, neLat, neLng)
				.stream()
				.filter(course -> q == null || course.getTitle().contains(q))
				.filter(course -> tag == null || course.getTags().contains(tag))
				.sorted(Comparator.comparing(Course::getCreatedAt).reversed())
				.limit(effectiveLimit)
				.map(course -> CourseSummaryResponse.from(course, isLikedByCurrentUser(course.getId(), currentUserId)))
				.toList();

		// MVP 범위: 커서 페이지네이션은 다음 작업에서 구현 (design.md "페이지네이션" 참고).
		return new ListResult(courses, new PageInfo(null));
	}

	@Transactional
	public CourseResponse update(String courseId, UpdateCourseRequest request) {
		Course course = findCourseOrThrow(courseId);
		requireAuthor(course);

		if (request.title() != null) course.setTitle(request.title());
		if (request.description() != null) course.setDescription(request.description());
		if (request.visibility() != null) course.setVisibility(request.visibility());
		if (request.tags() != null) course.replaceTags(request.tags());

		if (request.path() != null) {
			if (request.distanceMeters() == null || request.estimatedDurationSeconds() == null || request.bounds() == null) {
				throw new ApiException(
						ErrorCode.VALIDATION_ERROR,
						"path를 전송하면 distanceMeters, estimatedDurationSeconds, bounds도 함께 전송해야 합니다");
			}
			courseValidator.validate(request.path(), request.distanceMeters(), request.bounds());
			course.setPath(request.path());
			course.setDistanceMeters(request.distanceMeters());
			course.setEstimatedDurationSeconds(request.estimatedDurationSeconds());
			course.applyBounds(request.bounds());
		}

		course.setUpdatedAt(Instant.now());
		return CourseResponse.from(course, isLikedByCurrentUser(course.getId(), course.getAuthorId()));
	}

	@Transactional
	public void delete(String courseId) {
		Course course = findCourseOrThrow(courseId);
		requireAuthor(course);
		courseRepository.delete(course);
	}

	private Course findCourseOrThrow(String courseId) {
		return courseRepository.findById(courseId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "코스가 없습니다"));
	}

	private void requireAuthor(Course course) {
		String currentUserId = currentUserProvider.requireUserId();
		if (!course.getAuthorId().equals(currentUserId)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "작성자가 아닙니다");
		}
	}

	private boolean isLikedByCurrentUser(String courseId, String currentUserId) {
		if (currentUserId == null) return false;
		return likeRepository.existsById(new Like.LikeId(currentUserId, LikeTargetType.COURSE, courseId));
	}

	public record ListResult(List<CourseSummaryResponse> courses, PageInfo pageInfo) {
	}
}
