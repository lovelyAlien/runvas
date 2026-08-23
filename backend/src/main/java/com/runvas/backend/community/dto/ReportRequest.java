package com.runvas.backend.community.dto;

import com.runvas.backend.community.ReportReason;
import jakarta.validation.constraints.NotNull;

// docs/api-contract.md POST /reports/{targetType}/{targetId} 요청 본문.
public record ReportRequest(@NotNull ReportReason reason, String reasonDetail) {
}
