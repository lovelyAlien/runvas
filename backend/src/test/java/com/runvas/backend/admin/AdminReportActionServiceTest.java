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
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
	private final UserRepository userRepository = mock(UserRepository.class);
	private final AdminReportActionService adminReportActionService = new AdminReportActionService(
			reportRepository, postService, commentService, courseCommentService, userRepository);

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

	@Test
	void resolveAndBanDeletesContentAndBansAuthor() {
		UUID authorUuid = UUID.randomUUID();
		Report report = new Report("reporter-1", ReportTargetType.POST, "post-1", ReportReason.SPAM, null);
		when(reportRepository.findById("report-5")).thenReturn(Optional.of(report));
		when(reportRepository.findAllByTargetTypeAndTargetIdAndStatus(
						ReportTargetType.POST, "post-1", ReportStatus.PENDING))
				.thenReturn(List.of(report));
		when(postService.getAuthorId("post-1")).thenReturn(authorUuid.toString());
		User author = User.createKakaoUser(authorUuid.toString(), null, "Author", null);
		when(userRepository.findById(authorUuid)).thenReturn(Optional.of(author));

		adminReportActionService.resolveAndBan("report-5");

		verify(postService).getAuthorId("post-1");
		verify(postService).deleteAsAdmin("post-1");
		verify(userRepository).save(author);
		assertThat(author.isBanned()).isTrue();
		assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
	}

	@Test
	void resolveAndBanFetchesAuthorBeforeDeletingContent() {
		UUID authorUuid = UUID.randomUUID();
		Report report = new Report("reporter-1", ReportTargetType.COMMENT, "comment-1", ReportReason.SPAM, null);
		when(reportRepository.findById("report-6")).thenReturn(Optional.of(report));
		when(reportRepository.findAllByTargetTypeAndTargetIdAndStatus(
						ReportTargetType.COMMENT, "comment-1", ReportStatus.PENDING))
				.thenReturn(List.of(report));
		when(commentService.getAuthorId("comment-1")).thenReturn(authorUuid.toString());
		when(userRepository.findById(authorUuid)).thenReturn(Optional.empty());

		adminReportActionService.resolveAndBan("report-6");

		var inOrder = org.mockito.Mockito.inOrder(commentService);
		inOrder.verify(commentService).getAuthorId("comment-1");
		inOrder.verify(commentService).deleteAsAdmin("comment-1");
		verify(userRepository, never()).save(any());
	}

	@Test
	void resolveAndBanOnAlreadyResolvedReportIsNoOp() {
		Report alreadyResolved = new Report("reporter-1", ReportTargetType.POST, "post-1", ReportReason.SPAM, null);
		alreadyResolved.resolve();
		when(reportRepository.findById("report-7")).thenReturn(Optional.of(alreadyResolved));

		adminReportActionService.resolveAndBan("report-7");

		verify(postService, never()).getAuthorId(any());
		verify(postService, never()).deleteAsAdmin(any());
		verify(userRepository, never()).save(any());
	}
}
