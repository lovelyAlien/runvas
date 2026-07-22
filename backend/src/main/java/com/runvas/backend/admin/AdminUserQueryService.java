package com.runvas.backend.admin;

import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AdminUserQueryService {

    private final UserRepository userRepository;

    public AdminUserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<User> search(String q, int page, int size) {
        String keyword = q == null ? "" : q;
        return userRepository.findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                keyword, keyword, PageRequest.of(Math.max(0, page), size));
    }
}
