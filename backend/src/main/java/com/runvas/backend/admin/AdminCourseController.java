package com.runvas.backend.admin;

import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseVisibility;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminCourseController {

    private static final int PAGE_SIZE = 20;

    private final AdminCourseQueryService adminCourseQueryService;

    public AdminCourseController(AdminCourseQueryService adminCourseQueryService) {
        this.adminCourseQueryService = adminCourseQueryService;
    }

    @GetMapping("/admin/courses")
    String courses(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "visibility", required = false) CourseVisibility visibility,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model) {
        Page<Course> result = adminCourseQueryService.search(q, visibility, page, PAGE_SIZE);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("visibility", visibility);
        model.addAttribute("courses", result.getContent());
        model.addAttribute("page", result.getNumber());
        model.addAttribute("totalPages", result.getTotalPages());
        return "admin/courses";
    }
}
