package com.runvas.backend.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// docs/data-model.md CourseComment와 1:1. PUBLIC 코스에만 존재할 수 있다 (서비스 레이어에서 검증).
@Entity
@Table(name = "course_comments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseComment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Column(nullable = false)
	private String courseId;

	@Column(nullable = false)
	private String authorId;

	@Column
	private String parentCommentId;

	@Column(nullable = false, length = 1000)
	private String body;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Column(nullable = false)
	private Instant updatedAt = Instant.now();

	public CourseComment(String courseId, String authorId, String body) {
		this.courseId = courseId;
		this.authorId = authorId;
		this.body = body;
	}

	public CourseComment(String courseId, String authorId, String parentCommentId, String body) {
		this.courseId = courseId;
		this.authorId = authorId;
		this.parentCommentId = parentCommentId;
		this.body = body;
	}
}
