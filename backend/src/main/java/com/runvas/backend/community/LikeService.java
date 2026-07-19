package com.runvas.backend.community;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.community.dto.LikeResponse;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {

	private final LikeRepository likeRepository;
	private final CourseRepository courseRepository;
	private final PostRepository postRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public LikeResponse like(String targetTypePathValue, String targetId) {
		LikeTargetType targetType = parseTargetType(targetTypePathValue);
		String userId = currentUserProvider.requireUserId();

		Like.LikeId likeId = new Like.LikeId(userId, targetType, targetId);
		if (likeRepository.existsById(likeId)) {
			return new LikeResponse(targetTypePathValue, targetId, true, currentLikeCount(targetType, targetId));
		}

		requireTargetExists(targetType, targetId);
		likeRepository.save(new Like(userId, targetType, targetId));
		incrementLikeCount(targetType, targetId);

		return new LikeResponse(targetTypePathValue, targetId, true, currentLikeCount(targetType, targetId));
	}

	@Transactional
	public LikeResponse unlike(String targetTypePathValue, String targetId) {
		LikeTargetType targetType = parseTargetType(targetTypePathValue);
		String userId = currentUserProvider.requireUserId();

		Like.LikeId likeId = new Like.LikeId(userId, targetType, targetId);
		if (!likeRepository.existsById(likeId)) {
			return new LikeResponse(targetTypePathValue, targetId, false, currentLikeCount(targetType, targetId));
		}

		requireTargetExists(targetType, targetId);
		likeRepository.deleteById(likeId);
		decrementLikeCount(targetType, targetId);

		return new LikeResponse(targetTypePathValue, targetId, false, currentLikeCount(targetType, targetId));
	}

	@Transactional
	public void unlikeAllByUser(String userId) {
		List<Like> likes = likeRepository.findAllByIdUserId(userId);
		for (Like like : likes) {
			decrementLikeCount(like.getId().getTargetType(), like.getId().getTargetId());
		}
		likeRepository.deleteAllByIdUserId(userId);
	}

	private LikeTargetType parseTargetType(String value) {
		if ("courses".equals(value)) return LikeTargetType.COURSE;
		if ("posts".equals(value)) return LikeTargetType.POST;
		throw new ApiException(ErrorCode.VALIDATION_ERROR, "unsupported targetType: " + value);
	}

	private void requireTargetExists(LikeTargetType targetType, String targetId) {
		boolean exists = switch (targetType) {
			case COURSE -> courseRepository.existsById(targetId);
			case POST -> postRepository.existsById(targetId);
		};
		if (!exists) {
			throw new ApiException(ErrorCode.NOT_FOUND, "대상이 없습니다");
		}
	}

	private void incrementLikeCount(LikeTargetType targetType, String targetId) {
		switch (targetType) {
			case COURSE -> courseRepository.findById(targetId)
					.ifPresent(course -> course.setLikeCount(course.getLikeCount() + 1));
			case POST -> postRepository.findById(targetId).ifPresent(Post::incrementLikeCount);
		}
	}

	private void decrementLikeCount(LikeTargetType targetType, String targetId) {
		switch (targetType) {
			case COURSE -> courseRepository.findById(targetId)
					.ifPresent(course -> course.setLikeCount(Math.max(0, course.getLikeCount() - 1)));
			case POST -> postRepository.findById(targetId).ifPresent(Post::decrementLikeCount);
		}
	}

	private Integer currentLikeCount(LikeTargetType targetType, String targetId) {
		return switch (targetType) {
			case COURSE -> courseRepository.findById(targetId).map(Course::getLikeCount).orElse(0);
			case POST -> postRepository.findById(targetId).map(Post::getLikeCount).orElse(0);
		};
	}
}
