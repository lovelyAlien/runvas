package com.runvas.backend.community;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

// docs/data-model.md LikeTargetType — Course/Post 좋아요를 한 테이블로 통합.
@Entity
@Table(name = "likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Like {

	@EmbeddedId
	private LikeId id;

	private Instant createdAt = Instant.now();

	public Like(String userId, LikeTargetType targetType, String targetId) {
		this.id = new LikeId(userId, targetType, targetId);
	}

	@Getter
	@EqualsAndHashCode
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	public static class LikeId implements Serializable {
		private String userId;
		private LikeTargetType targetType;
		private String targetId;

		public LikeId(String userId, LikeTargetType targetType, String targetId) {
			this.userId = userId;
			this.targetType = targetType;
			this.targetId = targetId;
		}
	}
}
