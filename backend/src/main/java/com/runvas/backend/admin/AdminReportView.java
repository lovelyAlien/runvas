package com.runvas.backend.admin;

import com.runvas.backend.community.ReportReason;
import com.runvas.backend.community.ReportTargetType;
import java.time.Instant;

// /admin/reports 화면 전용 뷰 모델. Report에 콘텐츠 미리보기를 덧붙인다.
public record AdminReportView(
		String id,
		ReportTargetType targetType,
		String targetId,
		String contentPreview,
		String reporterId,
		ReportReason reason,
		String reasonDetail,
		Instant createdAt) {
}
