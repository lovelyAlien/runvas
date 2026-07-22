package com.runvas.backend.admin;

import com.runvas.backend.community.Post;
import com.runvas.backend.community.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AdminPostQueryService {

    private final PostRepository postRepository;

    public AdminPostQueryService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public Page<Post> search(String q, int page, int size) {
        String keyword = q == null ? "" : q;
        return postRepository.findByTitleContainingIgnoreCase(keyword, PageRequest.of(Math.max(0, page), size));
    }
}
