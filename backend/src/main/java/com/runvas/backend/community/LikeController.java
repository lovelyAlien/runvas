package com.runvas.backend.community;

import com.runvas.backend.community.dto.LikeResponse;
import lombok.RequiredArgsConstructor;
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
	public LikeResponse like(@PathVariable String targetType, @PathVariable String targetId) {
		return likeService.like(targetType, targetId);
	}

	@DeleteMapping("/{targetType}/{targetId}")
	public LikeResponse unlike(@PathVariable String targetType, @PathVariable String targetId) {
		return likeService.unlike(targetType, targetId);
	}
}
