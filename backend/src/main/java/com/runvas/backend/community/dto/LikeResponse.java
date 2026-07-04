package com.runvas.backend.community.dto;

public record LikeResponse(
		String targetType,
		String targetId,
		boolean liked,
		int likeCount
) {
}
