package com.runvas.backend.community;

import com.runvas.backend.community.dto.CommentResponse;
import com.runvas.backend.community.dto.CreateCommentRequest;
import com.runvas.backend.community.dto.UpdateCommentRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CommentController {

	private final CommentService commentService;

	@GetMapping("/api/posts/{postId}/comments")
	public Map<String, Object> list(
			@PathVariable String postId,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor) {
		CommentService.ListResult result = commentService.list(postId, limit);
		return Map.of("comments", result.comments(), "pageInfo", result.pageInfo());
	}

	@PostMapping("/api/posts/{postId}/comments")
	public ResponseEntity<Map<String, CommentResponse>> create(
			@PathVariable String postId, @Valid @RequestBody CreateCommentRequest request) {
		CommentResponse comment = commentService.create(postId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("comment", comment));
	}

	@PatchMapping("/api/comments/{commentId}")
	public Map<String, CommentResponse> update(
			@PathVariable String commentId, @Valid @RequestBody UpdateCommentRequest request) {
		return Map.of("comment", commentService.update(commentId, request));
	}

	@DeleteMapping("/api/comments/{commentId}")
	public ResponseEntity<Void> delete(@PathVariable String commentId) {
		commentService.delete(commentId);
		return ResponseEntity.noContent().build();
	}
}
