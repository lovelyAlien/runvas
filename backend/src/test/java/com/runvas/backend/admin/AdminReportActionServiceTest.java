package com.runvas.backend.admin;

import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.community.CommentService;
import com.runvas.backend.community.CourseCommentService;
import com.runvas.backend.community.PostService;
import com.runvas.backend.community.Report;
import com.runvas.backend.community.ReportReason;
import com.runvas.backend.community.ReportRepository;
import com.runvas.backend.community.ReportStatus;
import com.runvas.backend.community.ReportTargetType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminReportActionServiceTest {

	private final ReportRepository reportRepository = mock(ReportRepository.class);
	private final PostService postService = mock(PostService.class);
	private final CommentService commentService = mock(CommentService.class);
	private final CourseCommentService courseCommentService = mock(CourseCommentService.class);
	private final AdminReportActionService adminReportActionService =
			new AdminReportActionService(reportRepository, postService, commentService, courseCommentService);

	@Test
	void resolveDeletesPostAndResolvesAllPendingReportsForSameTarget() {
		Report target = new Report("reporter-1", ReportTargetType.POST, "post-1", ReportReason.SPAM, null);
		Report otherPendingForSameTarget =
				new Report("reporter-2", ReportTargetType.POST, "post-1", ReportReason.ABUSIVE, null);
		when(reportRepository.findById("report-1")).thenReturn(Optional.of(target));
		when(reportRepository.findAllByTargetTypeAndTargetIdAndStatus(
						ReportTargetType.POST, "post-1", ReportStatus.PENDING))
				.thenReturn(List.of(target, otherPendingForSameTarget));

		adminReportActionService.resolve("report-1");

		verify(postService).deleteAsAdmin("post-1");
		verify(commentService, never()).deleteAsAdmin(any());
		verify(courseCommentService, never()).deleteAsAdmin(any());
		assertThat(target.getStatus()).isEqualTo(ReportStatus.RESOLVED);
		assertThat(otherPendingForSameTarget.getStatus()).isEqualTo(ReportStatus.RESOLVED);
	}

	@Test
	void resolveOnUnknownReportThrowsNotFound() {
		when(reportRepository.findById("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> adminReportActionService.resolve("missing"))
				.isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void dismissMarksReportDismissedWithoutTouchingContent() {
		Report report = new Report("reporter-1", ReportTargetType.COMMENT, "comment-1", ReportReason.OTHER, "상세");
		when(reportRepository.findById("report-2")).thenReturn(Optional.of(report));

		adminReportActionService.dismiss("report-2");

		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
		verify(postService, never()).deleteAsAdmin(any());
		verify(commentService, never()).deleteAsAdmin(any());
		verify(courseCommentService, never()).deleteAsAdmin(any());
	}

	@Test
	void resolveOnAlreadyResolvedReportIsNoOp() {
		Report alreadyResolved = new Report("reporter-1", ReportTargetType.POST, "post-1", ReportReason.SPAM, null);
		alreadyResolved.resolve();
		when(reportRepository.findById("report-3")).thenReturn(Optional.of(alreadyResolved));

		adminReportActionService.resolve("report-3");

		verify(postService, never()).deleteAsAdmin(any());
		verify(reportRepository, never()).findAllByTargetTypeAndTargetIdAndStatus(any(), any(), any());
	}

	@Test
	void dismissOnAlreadyDismissedReportIsNoOp() {
		Report alreadyDismissed = new Report("reporter-1", ReportTargetType.COMMENT, "comment-1", ReportReason.OTHER, "상세");
		alreadyDismissed.dismiss();
		when(reportRepository.findById("report-4")).thenReturn(Optional.of(alreadyDismissed));
		Instant resolvedAtBefore = alreadyDismissed.getResolvedAt();

		adminReportActionService.dismiss("report-4");

		assertThat(alreadyDismissed.getResolvedAt()).isEqualTo(resolvedAtBefore);
	}
}
