package com.runvas.backend.community;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, String> {
	List<Comment> findByPostIdOrderByCreatedAtAsc(String postId);
}
