package com.runvas.backend.admin;

import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.community.CommentService;
import com.runvas.backend.community.CourseCommentService;
import com.runvas.backend.community.PostService;
import com.runvas.backend.community.Report;
import com.runvas.backend.community.ReportRepository;
import com.runvas.backend.community.ReportStatus;
import com.runvas.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReportActionService {

	private final ReportRepository reportRepository;
	private final PostService postService;
	private final CommentService commentService;
	private final CourseCommentService courseCommentService;
	private final UserRepository userRepository;

	@Transactional
	public void resolve(String reportId) {
		Report report = findOrThrow(reportId);
		if (report.getStatus() != ReportStatus.PENDING) {
			return;
		}

		switch (report.getTargetType()) {
			case POST -> postService.deleteAsAdmin(report.getTargetId());
			case COMMENT -> commentService.deleteAsAdmin(report.getTargetId());
			case COURSE_COMMENT -> courseCommentService.deleteAsAdmin(report.getTargetId());
		}

		reportRepository
				.findAllByTargetTypeAndTargetIdAndStatus(report.getTargetType(), report.getTargetId(), ReportStatus.PENDING)
				.forEach(Report::resolve);
	}

	@Transactional
	public void resolveAndBan(String reportId) {
		Report report = findOrThrow(reportId);
		if (report.getStatus() != ReportStatus.PENDING) {
			return;
		}

		String authorId = switch (report.getTargetType()) {
			case POST -> postService.getAuthorId(report.getTargetId());
			case COMMENT -> commentService.getAuthorId(report.getTargetId());
			case COURSE_COMMENT -> courseCommentService.getAuthorId(report.getTargetId());
		};

		resolve(reportId);

		userRepository.findById(UUID.fromString(authorId)).ifPresent(user -> {
			user.ban();
			userRepository.save(user);
		});
	}

	@Transactional
	public void dismiss(String reportId) {
		Report report = findOrThrow(reportId);
		if (report.getStatus() != ReportStatus.PENDING) {
			return;
		}
		report.dismiss();
	}

	private Report findOrThrow(String reportId) {
		return reportRepository.findById(reportId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "신고가 없습니다"));
	}
}
