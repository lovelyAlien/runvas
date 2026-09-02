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

// docs/data-model.md Block — Bookmark와 동일한 복합키 관계 테이블. 단방향 차단이라 상태 필드가 없다.
@Entity
@Table(name = "blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block {

	@EmbeddedId
	private BlockId id;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	public Block(String blockerId, String blockedId) {
		this.id = new BlockId(blockerId, blockedId);
	}

	public String getBlockerId() {
		return id.getBlockerId();
	}

	public String getBlockedId() {
		return id.getBlockedId();
	}

	@Getter
	@EqualsAndHashCode
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	public static class BlockId implements Serializable {
		private String blockerId;
		private String blockedId;

		public BlockId(String blockerId, String blockedId) {
			this.blockerId = blockerId;
			this.blockedId = blockedId;
		}
	}
}
