package com.runvas.backend.community;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.community.dto.LikeResponse;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {

	private final LikeRepository likeRepository;
	private final CourseRepository courseRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public LikeResponse put(String targetTypeSegment, String targetId) {
		String userId = currentUserProvider.requireUserId();
		LikeTargetType targetType = resolveTargetType(targetTypeSegment);
		Like.LikeId likeId = new Like.LikeId(userId, targetType, targetId);

		if (!likeRepository.existsById(likeId)) {
			likeRepository.save(new Like(userId, targetType, targetId));
			adjustLikeCount(targetType, targetId, 1);
		}

		int likeCount = getCurrentLikeCount(targetType, targetId);
		return new LikeResponse(targetType.name(), targetId, true, likeCount);
	}

	@Transactional
	public LikeResponse delete(String targetTypeSegment, String targetId) {
		String userId = currentUserProvider.requireUserId();
		LikeTargetType targetType = resolveTargetType(targetTypeSegment);
		Like.LikeId likeId = new Like.LikeId(userId, targetType, targetId);

		if (likeRepository.existsById(likeId)) {
			likeRepository.deleteById(likeId);
			adjustLikeCount(targetType, targetId, -1);
		}

		int likeCount = getCurrentLikeCount(targetType, targetId);
		return new LikeResponse(targetType.name(), targetId, false, likeCount);
	}

	private LikeTargetType resolveTargetType(String segment) {
		return switch (segment) {
			case "courses" -> LikeTargetType.COURSE;
			default -> throw new ApiException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 대상 타입입니다: " + segment);
		};
	}

	private void adjustLikeCount(LikeTargetType targetType, String targetId, int delta) {
		if (targetType == LikeTargetType.COURSE) {
			Course course = courseRepository.findById(targetId)
					.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "코스를 찾을 수 없습니다."));
			course.setLikeCount(Math.max(0, course.getLikeCount() + delta));
		}
	}

	private int getCurrentLikeCount(LikeTargetType targetType, String targetId) {
		if (targetType == LikeTargetType.COURSE) {
			return courseRepository.findById(targetId)
					.map(Course::getLikeCount)
					.orElse(0);
		}
		return 0;
	}
}
