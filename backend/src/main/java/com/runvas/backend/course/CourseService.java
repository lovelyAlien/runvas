package com.runvas.backend.course;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.common.PageInfo;
import com.runvas.backend.common.RoutePoint;
import com.runvas.backend.community.Like;
import com.runvas.backend.community.LikeRepository;
import com.runvas.backend.community.LikeTargetType;
import com.runvas.backend.course.dto.CourseResponse;
import com.runvas.backend.course.dto.CourseSummaryResponse;
import com.runvas.backend.course.dto.CreateCourseRequest;
import com.runvas.backend.course.dto.UpdateCourseRequest;
import com.runvas.backend.routing.TmapReverseGeocodingClient;
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
	private final TmapReverseGeocodingClient reverseGeocodingClient;

	@Transactional
	public CourseResponse create(CreateCourseRequest request) {
		String authorId = currentUserProvider.requireUserId();
		courseValidator.validate(request.path(), request.waypoints(), request.distanceMeters(), request.bounds());

		Course course = new Course(
				authorId,
				request.title(),
				request.description(),
				request.path(),
				request.waypoints(),
				request.distanceMeters(),
				request.estimatedDurationSeconds(),
				request.bounds(),
				request.visibility(),
				request.tags() == null ? Set.of() : request.tags());

		course.setStartAddress(resolveStartAddress(request.path()));
		courseRepository.save(course);
		return CourseResponse.from(course, false);
	}

	// tags가 지연 로딩 컬렉션이라, open-in-view=false에서 트랜잭션 밖으로 나가면
	// CourseResponse.from()이 course.getTags()를 읽을 때 LazyInitializationException이 난다
	// (listMine()에서 같은 문제를 겪고 고친 것과 동일 — CourseDetailScreen에서 실제로 재현됨).
	@Transactional(readOnly = true)
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

	// getById()/listMine()과 같은 이유로 필요 — tags 지연 로딩 컬렉션을 트랜잭션 안에서 복사해야 한다.
	@Transactional(readOnly = true)
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

	// tags가 지연 로딩 컬렉션이라, open-in-view=false인 상태에서 트랜잭션 밖으로 나가면
	// CourseSummaryResponse.from()이 course.getTags()를 읽을 때 LazyInitializationException이 난다.
	@Transactional(readOnly = true)
	public List<CourseSummaryResponse> listMine() {
		String currentUserId = currentUserProvider.requireUserId();
		return courseRepository.findByAuthorIdOrderByCreatedAtDesc(currentUserId).stream()
				.map(course -> CourseSummaryResponse.from(course, isLikedByCurrentUser(course.getId(), currentUserId)))
				.toList();
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
			if (request.waypoints() == null
					|| request.distanceMeters() == null
					|| request.estimatedDurationSeconds() == null
					|| request.bounds() == null) {
				throw new ApiException(
						ErrorCode.VALIDATION_ERROR,
						"path를 전송하면 waypoints, distanceMeters, estimatedDurationSeconds, bounds도 함께 전송해야 합니다");
			}
			courseValidator.validate(request.path(), request.waypoints(), request.distanceMeters(), request.bounds());
			course.setPath(request.path());
			course.setWaypoints(request.waypoints());
			course.setDistanceMeters(request.distanceMeters());
			course.setEstimatedDurationSeconds(request.estimatedDurationSeconds());
			course.applyBounds(request.bounds());
			course.setStartAddress(resolveStartAddress(request.path()));
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

	private String resolveStartAddress(List<RoutePoint> path) {
		if (path == null || path.isEmpty()) return null;
		RoutePoint start = path.get(0);
		return reverseGeocodingClient.fetchAddress(start.latitude(), start.longitude());
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
