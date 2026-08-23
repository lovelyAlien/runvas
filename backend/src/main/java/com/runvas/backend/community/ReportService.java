package com.runvas.backend.community;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.community.dto.ReportRequest;
import com.runvas.backend.community.dto.ReportResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

	private final ReportRepository reportRepository;
	private final PostRepository postRepository;
	private final CommentRepository commentRepository;
	private final CourseCommentRepository courseCommentRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public Result report(String targetTypePathValue, String targetId, ReportRequest request) {
		validateReasonDetail(request.reason(), request.reasonDetail());
		ReportTargetType targetType = parseTargetType(targetTypePathValue);
		String reporterId = currentUserProvider.requireUserId();
		requireTargetExists(targetType, targetId);

		Optional<Report> existing = reportRepository.findByReporterIdAndTargetTypeAndTargetIdAndStatus(
				reporterId, targetType, targetId, ReportStatus.PENDING);
		if (existing.isPresent()) {
			return new Result(ReportResponse.from(targetTypePathValue, existing.get()), false);
		}

		Report saved = reportRepository.save(
				new Report(reporterId, targetType, targetId, request.reason(), request.reasonDetail()));
		return new Result(ReportResponse.from(targetTypePathValue, saved), true);
	}

	private void validateReasonDetail(ReportReason reason, String reasonDetail) {
		if (reason == ReportReason.OTHER && (reasonDetail == null || reasonDetail.isBlank())) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "reasonDetail is required when reason is OTHER");
		}
		if (reasonDetail != null && reasonDetail.length() > 200) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "reasonDetail must be at most 200 characters");
		}
	}

	private ReportTargetType parseTargetType(String value) {
		return switch (value) {
			case "posts" -> ReportTargetType.POST;
			case "comments" -> ReportTargetType.COMMENT;
			case "course-comments" -> ReportTargetType.COURSE_COMMENT;
			default -> throw new ApiException(ErrorCode.VALIDATION_ERROR, "unsupported targetType: " + value);
		};
	}

	private void requireTargetExists(ReportTargetType targetType, String targetId) {
		boolean exists = switch (targetType) {
			case POST -> postRepository.existsById(targetId);
			case COMMENT -> commentRepository.existsById(targetId);
			case COURSE_COMMENT -> courseCommentRepository.existsById(targetId);
		};
		if (!exists) {
			throw new ApiException(ErrorCode.NOT_FOUND, "대상이 없습니다");
		}
	}

	public record Result(ReportResponse response, boolean isNew) {
	}
}
