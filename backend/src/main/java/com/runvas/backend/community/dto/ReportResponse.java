package com.runvas.backend.community.dto;

import com.runvas.backend.community.Report;
import java.time.Instant;

// docs/api-contract.md POST /reports/{targetType}/{targetId} 응답.
public record ReportResponse(String id, String targetType, String targetId, String status, Instant createdAt) {
	public static ReportResponse from(String targetTypePathValue, Report report) {
		return new ReportResponse(
				report.getId(), targetTypePathValue, report.getTargetId(), report.getStatus().name(), report.getCreatedAt());
	}
}
