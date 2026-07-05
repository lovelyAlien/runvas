package com.runvas.backend.community;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.common.PageInfo;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.dto.BookmarkedCourseSummaryResponse;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

	private final BookmarkRepository bookmarkRepository;
	private final CourseRepository courseRepository;
	private final LikeRepository likeRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public BookmarkResponse add(String courseId) {
		String userId = currentUserProvider.requireUserId();
		courseRepository.findById(courseId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "코스가 없습니다"));

		Bookmark.BookmarkId bookmarkId = new Bookmark.BookmarkId(userId, courseId);
		if (!bookmarkRepository.existsById(bookmarkId)) {
			bookmarkRepository.save(new Bookmark(userId, courseId));
		}

		Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
				.orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "북마크를 찾을 수 없습니다"));
		return new BookmarkResponse(new BookmarkResponse.BookmarkInfo(courseId, bookmark.getCreatedAt()));
	}

	@Transactional
	public void remove(String courseId) {
		String userId = currentUserProvider.requireUserId();
		Bookmark.BookmarkId bookmarkId = new Bookmark.BookmarkId(userId, courseId);
		if (bookmarkRepository.existsById(bookmarkId)) {
			bookmarkRepository.deleteById(bookmarkId);
		}
	}

	@Transactional(readOnly = true)
	public ListResult listByUser() {
		String userId = currentUserProvider.requireUserId();
		List<Bookmark> bookmarks = bookmarkRepository.findByIdUserIdOrderByCreatedAtDesc(userId);

		List<BookmarkedCourseSummaryResponse> courses = bookmarks.stream()
				.flatMap(bookmark -> courseRepository.findById(bookmark.getCourseId())
						.stream()
						.map(course -> BookmarkedCourseSummaryResponse.from(
								course,
								isLikedByCurrentUser(course, userId),
								bookmark)))
				.toList();

		return new ListResult(courses, new PageInfo(null));
	}

	private boolean isLikedByCurrentUser(Course course, String userId) {
		return likeRepository.existsById(new Like.LikeId(userId, LikeTargetType.COURSE, course.getId()));
	}

	public record BookmarkResponse(BookmarkInfo bookmark) {
		public record BookmarkInfo(String courseId, Instant createdAt) {}
	}

	public record ListResult(List<BookmarkedCourseSummaryResponse> courses, PageInfo pageInfo) {}
}
