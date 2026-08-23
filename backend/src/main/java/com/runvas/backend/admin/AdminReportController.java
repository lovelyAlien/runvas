package com.runvas.backend.admin;

import com.runvas.backend.community.ReportStatus;
import com.runvas.backend.community.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminReportController {

	private static final int PAGE_SIZE = 20;

	private final AdminReportQueryService adminReportQueryService;
	private final AdminReportActionService adminReportActionService;

	public AdminReportController(
			AdminReportQueryService adminReportQueryService, AdminReportActionService adminReportActionService) {
		this.adminReportQueryService = adminReportQueryService;
		this.adminReportActionService = adminReportActionService;
	}

	@GetMapping("/admin/reports")
	String reports(
			@RequestParam(name = "status", defaultValue = "PENDING") ReportStatus status,
			@RequestParam(name = "targetType", required = false) ReportTargetType targetType,
			@RequestParam(name = "page", defaultValue = "0") int page,
			Model model) {
		Page<AdminReportView> result = adminReportQueryService.search(status, targetType, page, PAGE_SIZE);
		model.addAttribute("status", status);
		model.addAttribute("targetType", targetType);
		model.addAttribute("reports", result.getContent());
		model.addAttribute("page", result.getNumber());
		model.addAttribute("totalPages", result.getTotalPages());
		return "admin/reports";
	}

	@PostMapping("/admin/reports/{reportId}/resolve")
	String resolve(@PathVariable String reportId) {
		adminReportActionService.resolve(reportId);
		return "redirect:/admin/reports";
	}

	@PostMapping("/admin/reports/{reportId}/dismiss")
	String dismiss(@PathVariable String reportId) {
		adminReportActionService.dismiss(reportId);
		return "redirect:/admin/reports";
	}
}
