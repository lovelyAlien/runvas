package com.runvas.backend.community.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// docs/api-contract.md PATCH /comments/{commentId} 요청 본문 — body는 필수.
public record UpdateCommentRequest(@NotNull @Size(min = 1, max = 1000) String body) {
}
