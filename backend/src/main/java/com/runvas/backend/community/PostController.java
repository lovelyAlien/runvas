package com.runvas.backend.community;

import com.runvas.backend.community.dto.CreatePostRequest;
import com.runvas.backend.community.dto.PostResponse;
import com.runvas.backend.community.dto.UpdatePostRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

	private final PostService postService;

	@PostMapping
	public ResponseEntity<Map<String, PostResponse>> create(@Valid @RequestBody CreatePostRequest request) {
		PostResponse post = postService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("post", post));
	}

	@GetMapping
	public Map<String, Object> list(
			@RequestParam(required = false) String attachedCourseId,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String tag,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor) {
		PostService.ListResult result = postService.list(attachedCourseId, q, tag, sort, limit);
		return Map.of("posts", result.posts(), "pageInfo", result.pageInfo());
	}

	@GetMapping("/{postId}")
	public Map<String, PostResponse> getById(@PathVariable String postId) {
		return Map.of("post", postService.getById(postId));
	}

	@PatchMapping("/{postId}")
	public Map<String, PostResponse> update(
			@PathVariable String postId, @Valid @RequestBody UpdatePostRequest request) {
		return Map.of("post", postService.update(postId, request));
	}

	@DeleteMapping("/{postId}")
	public ResponseEntity<Void> delete(@PathVariable String postId) {
		postService.delete(postId);
		return ResponseEntity.noContent().build();
	}
}
