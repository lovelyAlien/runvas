package com.runvas.backend.community;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bookmarks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark {

	@EmbeddedId
	private BookmarkId id;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	public Bookmark(String userId, String courseId) {
		this.id = new BookmarkId(userId, courseId);
	}

	public String getUserId() {
		return id.getUserId();
	}

	public String getCourseId() {
		return id.getCourseId();
	}

	@Getter
	@EqualsAndHashCode
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	public static class BookmarkId implements Serializable {

		private String userId;
		private String courseId;

		public BookmarkId(String userId, String courseId) {
			this.userId = userId;
			this.courseId = courseId;
		}
	}
}
