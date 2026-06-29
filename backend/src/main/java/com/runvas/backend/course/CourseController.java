package com.runvas.backend.course;

import com.runvas.backend.course.dto.CourseResponse;
import com.runvas.backend.course.dto.CreateCourseRequest;
import com.runvas.backend.course.dto.UpdateCourseRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

	private final CourseService courseService;

	@PostMapping
	public ResponseEntity<Map<String, CourseResponse>> create(@Valid @RequestBody CreateCourseRequest request) {
		CourseResponse course = courseService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("course", course));
	}

	@GetMapping
	public Map<String, Object> list(
			@RequestParam double swLat,
			@RequestParam double swLng,
			@RequestParam double neLat,
			@RequestParam double neLng,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String tag,
			@RequestParam(required = false) String sort) {
		CourseService.ListResult result = courseService.list(swLat, swLng, neLat, neLng, limit, q, tag);
		return Map.of("courses", result.courses(), "pageInfo", result.pageInfo());
	}

	@GetMapping("/mine")
	public Map<String, Object> listMine() {
		return Map.of("courses", courseService.listMine());
	}

	@GetMapping("/{courseId}")
	public Map<String, CourseResponse> getById(@PathVariable String courseId) {
		return Map.of("course", courseService.getById(courseId));
	}

	@PatchMapping("/{courseId}")
	public Map<String, CourseResponse> update(
			@PathVariable String courseId, @Valid @RequestBody UpdateCourseRequest request) {
		return Map.of("course", courseService.update(courseId, request));
	}

	@DeleteMapping("/{courseId}")
	public ResponseEntity<Void> delete(@PathVariable String courseId) {
		courseService.delete(courseId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{courseId}/gpx")
	public ResponseEntity<String> downloadGpx(@PathVariable String courseId) {
		CourseResponse course = courseService.getById(courseId);
		String gpx = GpxBuilder.build(course.title(), course.path());
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/gpx+xml"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + courseId + ".gpx\"")
				.body(gpx);
	}
}
