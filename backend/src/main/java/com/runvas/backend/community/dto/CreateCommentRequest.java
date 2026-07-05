package com.runvas.backend.community.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// docs/api-contract.md POST /posts/{postId}/comments 요청 본문.
public record CreateCommentRequest(@NotNull @Size(min = 1, max = 1000) String body) {
}
