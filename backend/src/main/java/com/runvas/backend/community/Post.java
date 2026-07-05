package com.runvas.backend.community;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// docs/data-model.md Post와 1:1. likeCount/commentCount는 저장하는 비정규화 카운터
// (Course.likeCount와 동일한 이유 — Like/Comment 테이블을 매번 세지 않는다).
@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Column(nullable = false)
	private String authorId;

	@Column(nullable = false, length = 80)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	private String attachedCourseId;

	@ElementCollection
	@CollectionTable(name = "post_tags", joinColumns = @JoinColumn(name = "post_id"))
	@Column(name = "tag", length = 20)
	private Set<String> tags = new LinkedHashSet<>();

	@Column(nullable = false)
	private Integer likeCount = 0;

	@Column(nullable = false)
	private Integer commentCount = 0;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Column(nullable = false)
	private Instant updatedAt = Instant.now();

	public Post(String authorId, String title, String body, String attachedCourseId, Set<String> tags) {
		this.authorId = authorId;
		this.title = title;
		this.body = body;
		this.attachedCourseId = attachedCourseId;
		this.tags = new LinkedHashSet<>(tags);
	}

	public void replaceTags(Set<String> newTags) {
		this.tags = new LinkedHashSet<>(newTags);
	}

	public void incrementCommentCount() {
		this.commentCount = this.commentCount + 1;
	}

	public void decrementCommentCount() {
		this.commentCount = Math.max(0, this.commentCount - 1);
	}

	public void incrementLikeCount() {
		this.likeCount = this.likeCount + 1;
	}

	public void decrementLikeCount() {
		this.likeCount = Math.max(0, this.likeCount - 1);
	}
}
