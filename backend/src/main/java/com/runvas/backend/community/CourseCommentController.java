package com.runvas.backend.community;

import com.runvas.backend.community.dto.CourseCommentResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/courses/{courseId}/comments")
@RequiredArgsConstructor
public class CourseCommentController {

	private final CourseCommentService courseCommentService;

	@GetMapping
	public Map<String, Object> list(
			@PathVariable String courseId,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor) {
		CourseCommentService.ListResult result = courseCommentService.list(courseId, limit, cursor);
		return Map.of("comments", result.comments(), "pageInfo", result.pageInfo());
	}

	@PostMapping
	public ResponseEntity<Map<String, CourseCommentResponse>> create(
			@PathVariable String courseId,
			@RequestParam String body,
			@RequestParam(required = false) MultipartFile image,
			@RequestParam(required = false) String parentCommentId) {
		CourseCommentResponse response = courseCommentService.create(courseId, body, image, parentCommentId);
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("comment", response));
	}

	@GetMapping("/{commentId}/replies")
	public Map<String, Object> listReplies(@PathVariable String courseId, @PathVariable String commentId) {
		return Map.of("replies", courseCommentService.listReplies(courseId, commentId));
	}

	@PatchMapping("/{commentId}")
	public Map<String, CourseCommentResponse> update(
			@PathVariable String courseId,
			@PathVariable String commentId,
			@RequestParam(required = false) String body,
			@RequestParam(required = false) MultipartFile image,
			@RequestParam(required = false) Boolean removeImage) {
		CourseCommentResponse response =
				courseCommentService.update(courseId, commentId, body, image, removeImage);
		return Map.of("comment", response);
	}

	@DeleteMapping("/{commentId}")
	public ResponseEntity<Void> delete(@PathVariable String courseId, @PathVariable String commentId) {
		courseCommentService.delete(courseId, commentId);
		return ResponseEntity.noContent().build();
	}
}
