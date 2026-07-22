package com.runvas.backend.admin;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminDashboardController {

    private final AdminStatsService adminStatsService;

    public AdminDashboardController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/admin/dashboard")
    String dashboard(Model model) {
        List<DailyTrendPoint> userSignupTrend = adminStatsService.userSignupTrend();
        List<DailyTrendPoint> courseCreationTrend = adminStatsService.courseCreationTrend();
        List<DailyTrendPoint> postCreationTrend = adminStatsService.postCreationTrend();

        model.addAttribute("summary", adminStatsService.summary());
        model.addAttribute("trendLabels", labelsOf(userSignupTrend));
        model.addAttribute("userSignupCounts", countsOf(userSignupTrend));
        model.addAttribute("courseCreationCounts", countsOf(courseCreationTrend));
        model.addAttribute("postCreationCounts", countsOf(postCreationTrend));
        return "admin/dashboard";
    }

    private List<String> labelsOf(List<DailyTrendPoint> trend) {
        return trend.stream().map(point -> point.day().toString()).toList();
    }

    private List<Long> countsOf(List<DailyTrendPoint> trend) {
        return trend.stream().map(DailyTrendPoint::count).toList();
    }
}
