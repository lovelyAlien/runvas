package com.runvas.backend.community.dto;

// docs/api-contract.md PUT/DELETE /likes/{targetType}/{targetId} 응답.
public record LikeResponse(String targetType, String targetId, boolean liked, Integer likeCount) {
}
