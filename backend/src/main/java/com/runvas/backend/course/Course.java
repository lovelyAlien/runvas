package com.runvas.backend.course;

import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.GeoPoint;
import com.runvas.backend.common.RoutePoint;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// docs/data-model.md Course와 1:1. likeCount는 저장하고, likedByMe는 요청 시점에 계산한다
// (Like 테이블 조회 — 저장 필드 아님).
@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Column(nullable = false)
	private String authorId;

	@Column(nullable = false, length = 60)
	private String title;

	@Column(length = 500)
	private String description;

	@Lob
	@Convert(converter = RoutePointListConverter.class)
	@Column(nullable = false, columnDefinition = "LONGTEXT")
	private List<RoutePoint> path;

	@Column(nullable = false)
	private Integer distanceMeters;

	@Column(nullable = false)
	private Integer estimatedDurationSeconds;

	@Column(nullable = false)
	private Double swLat;

	@Column(nullable = false)
	private Double swLng;

	@Column(nullable = false)
	private Double neLat;

	@Column(nullable = false)
	private Double neLng;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CourseVisibility visibility;

	@ElementCollection
	@CollectionTable(name = "course_tags", joinColumns = @jakarta.persistence.JoinColumn(name = "course_id"))
	@Column(name = "tag", length = 20)
	private Set<String> tags = new LinkedHashSet<>();

	@Column(nullable = false)
	private Integer likeCount = 0;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Column(nullable = false)
	private Instant updatedAt = Instant.now();

	public Course(
			String authorId,
			String title,
			String description,
			List<RoutePoint> path,
			Integer distanceMeters,
			Integer estimatedDurationSeconds,
			GeoBounds bounds,
			CourseVisibility visibility,
			Set<String> tags) {
		this.authorId = authorId;
		this.title = title;
		this.description = description;
		this.path = path;
		this.distanceMeters = distanceMeters;
		this.estimatedDurationSeconds = estimatedDurationSeconds;
		applyBounds(bounds);
		this.visibility = visibility;
		this.tags = new LinkedHashSet<>(tags);
	}

	public void applyBounds(GeoBounds bounds) {
		this.swLat = bounds.southWest().latitude();
		this.swLng = bounds.southWest().longitude();
		this.neLat = bounds.northEast().latitude();
		this.neLng = bounds.northEast().longitude();
	}

	public GeoBounds getBounds() {
		return new GeoBounds(new GeoPoint(swLat, swLng), new GeoPoint(neLat, neLng));
	}

	public void replaceTags(Set<String> newTags) {
		this.tags = new LinkedHashSet<>(newTags);
	}
}
