package com.runvas.backend.admin;

import com.runvas.backend.community.Post;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminPostController {

    private static final int PAGE_SIZE = 20;

    private final AdminPostQueryService adminPostQueryService;

    public AdminPostController(AdminPostQueryService adminPostQueryService) {
        this.adminPostQueryService = adminPostQueryService;
    }

    @GetMapping("/admin/posts")
    String posts(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model) {
        Page<Post> result = adminPostQueryService.search(q, page, PAGE_SIZE);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("posts", result.getContent());
        model.addAttribute("page", result.getNumber());
        model.addAttribute("totalPages", result.getTotalPages());
        return "admin/posts";
    }
}
