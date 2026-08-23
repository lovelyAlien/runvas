package com.runvas.backend.admin;

import com.runvas.backend.community.Comment;
import com.runvas.backend.community.CommentRepository;
import com.runvas.backend.community.CourseComment;
import com.runvas.backend.community.CourseCommentRepository;
import com.runvas.backend.community.Post;
import com.runvas.backend.community.PostRepository;
import com.runvas.backend.community.Report;
import com.runvas.backend.community.ReportRepository;
import com.runvas.backend.community.ReportStatus;
import com.runvas.backend.community.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AdminReportQueryService {

	private final ReportRepository reportRepository;
	private final PostRepository postRepository;
	private final CommentRepository commentRepository;
	private final CourseCommentRepository courseCommentRepository;

	public AdminReportQueryService(
			ReportRepository reportRepository,
			PostRepository postRepository,
			CommentRepository commentRepository,
			CourseCommentRepository courseCommentRepository) {
		this.reportRepository = reportRepository;
		this.postRepository = postRepository;
		this.commentRepository = commentRepository;
		this.courseCommentRepository = courseCommentRepository;
	}

	public Page<AdminReportView> search(ReportStatus status, ReportTargetType targetType, int page, int size) {
		PageRequest pageable = PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.ASC, "createdAt"));
		Page<Report> reports = targetType == null
				? reportRepository.findAllByStatus(status, pageable)
				: reportRepository.findAllByStatusAndTargetType(status, targetType, pageable);
		return reports.map(this::toView);
	}

	private AdminReportView toView(Report report) {
		String preview = switch (report.getTargetType()) {
			case POST -> postRepository.findById(report.getTargetId()).map(Post::getTitle).orElse(null);
			case COMMENT -> commentRepository.findById(report.getTargetId()).map(Comment::getBody).orElse(null);
			case COURSE_COMMENT -> courseCommentRepository.findById(report.getTargetId())
					.map(CourseComment::getBody)
					.orElse(null);
		};
		return new AdminReportView(
				report.getId(),
				report.getTargetType(),
				report.getTargetId(),
				preview,
				report.getReporterId(),
				report.getReason(),
				report.getReasonDetail(),
				report.getCreatedAt());
	}
}
