package com.runvas.backend.admin;

import com.runvas.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminUserController {

    private static final int PAGE_SIZE = 20;

    private final AdminUserQueryService adminUserQueryService;

    public AdminUserController(AdminUserQueryService adminUserQueryService) {
        this.adminUserQueryService = adminUserQueryService;
    }

    @GetMapping("/admin/users")
    String users(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model) {
        Page<User> result = adminUserQueryService.search(q, page, PAGE_SIZE);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("users", result.getContent());
        model.addAttribute("page", result.getNumber());
        model.addAttribute("totalPages", result.getTotalPages());
        return "admin/users";
    }
}
