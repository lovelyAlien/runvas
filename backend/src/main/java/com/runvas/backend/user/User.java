package com.runvas.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// docs/data-model.md User와 1:1. providerUserId는 내부 저장값이라 어떤 응답 DTO에도 넣지 않는다.
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Column(unique = true)
	private String email;

	@Column(nullable = false)
	private String provider;

	@Column(name = "provider_user_id", nullable = false)
	private String providerUserId;

	@Column(nullable = false, unique = true)
	private String nickname;

	private String profileImageUrl;

	private String bio;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Column(nullable = false)
	private Instant updatedAt = Instant.now();

	public User(String email, String provider, String providerUserId, String nickname) {
		this.email = email;
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.nickname = nickname;
	}
}
