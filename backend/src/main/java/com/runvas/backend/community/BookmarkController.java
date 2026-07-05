package com.runvas.backend.community;

import com.runvas.backend.community.BookmarkService.BookmarkResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses/{courseId}/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

	private final BookmarkService bookmarkService;

	@PostMapping
	public ResponseEntity<BookmarkResponse> add(@PathVariable String courseId) {
		BookmarkResponse response = bookmarkService.add(courseId);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@DeleteMapping
	public ResponseEntity<Void> remove(@PathVariable String courseId) {
		bookmarkService.remove(courseId);
		return ResponseEntity.noContent().build();
	}
}
