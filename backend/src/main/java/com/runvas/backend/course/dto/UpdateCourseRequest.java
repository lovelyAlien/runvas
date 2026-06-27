package com.runvas.backend.course.dto;

import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.RoutePoint;
import com.runvas.backend.course.CourseVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

// docs/api-contract.md PATCH /courses/{id} — 전송한 필드만 수정, path/tags는 전체 교체.
public record UpdateCourseRequest(
		@Size(min = 1, max = 60) String title,
		@Size(max = 500) String description,
		@Valid List<RoutePoint> path,
		Integer distanceMeters,
		Integer estimatedDurationSeconds,
		@Valid GeoBounds bounds,
		CourseVisibility visibility,
		@Size(max = 10) Set<String> tags) {
}
