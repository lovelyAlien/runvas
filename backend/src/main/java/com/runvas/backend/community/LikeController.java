package com.runvas.backend.community;

import com.runvas.backend.community.dto.LikeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikeController {

	private final LikeService likeService;

	@PutMapping("/{targetType}/{targetId}")
	public ResponseEntity<LikeResponse> put(
			@PathVariable String targetType,
			@PathVariable String targetId
	) {
		return ResponseEntity.ok(likeService.put(targetType, targetId));
	}

	@DeleteMapping("/{targetType}/{targetId}")
	public ResponseEntity<LikeResponse> delete(
			@PathVariable String targetType,
			@PathVariable String targetId
	) {
		return ResponseEntity.ok(likeService.delete(targetType, targetId));
	}
}
