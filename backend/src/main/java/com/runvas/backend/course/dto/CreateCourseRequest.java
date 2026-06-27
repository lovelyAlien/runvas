package com.runvas.backend.course.dto;

import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.RoutePoint;
import com.runvas.backend.course.CourseVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

// docs/api-contract.md POST /courses 요청 본문, docs/data-model.md Course 제한값.
public record CreateCourseRequest(
		@NotNull @Size(min = 1, max = 60) String title,
		@Size(max = 500) String description,
		@NotEmpty @Valid List<RoutePoint> path,
		@NotNull Integer distanceMeters,
		@NotNull Integer estimatedDurationSeconds,
		@NotNull @Valid GeoBounds bounds,
		@NotNull CourseVisibility visibility,
		@Size(max = 10) Set<String> tags) {
}
