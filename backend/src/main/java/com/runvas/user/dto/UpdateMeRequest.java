package com.runvas.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateMeRequest(
        @Size(min = 2, max = 30) String nickname,
        @Size(max = 1000) String profileImageUrl,
        @Size(max = 160) String bio,
        @Min(120) @Max(900) Integer runningPaceSecPerKm
) {
}
