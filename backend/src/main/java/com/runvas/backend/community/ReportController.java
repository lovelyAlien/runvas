package com.runvas.backend.community;

import com.runvas.backend.community.dto.ReportRequest;
import com.runvas.backend.community.dto.ReportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;

	@PostMapping("/{targetType}/{targetId}")
	public ResponseEntity<ReportResponse> report(
			@PathVariable String targetType,
			@PathVariable String targetId,
			@Valid @RequestBody ReportRequest request) {
		ReportService.Result result = reportService.report(targetType, targetId, request);
		HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(result.response());
	}
}
