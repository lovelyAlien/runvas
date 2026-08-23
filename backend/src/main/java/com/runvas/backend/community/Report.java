package com.runvas.backend.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// docs/data-model.md Report와 1:1.
@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Column(nullable = false)
	private String reporterId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportTargetType targetType;

	@Column(nullable = false)
	private String targetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportReason reason;

	@Column(length = 200)
	private String reasonDetail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportStatus status = ReportStatus.PENDING;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	private Instant resolvedAt;

	public Report(String reporterId, ReportTargetType targetType, String targetId, ReportReason reason, String reasonDetail) {
		this.reporterId = reporterId;
		this.targetType = targetType;
		this.targetId = targetId;
		this.reason = reason;
		this.reasonDetail = reasonDetail;
	}

	public void resolve() {
		this.status = ReportStatus.RESOLVED;
		this.resolvedAt = Instant.now();
	}

	public void dismiss() {
		this.status = ReportStatus.DISMISSED;
		this.resolvedAt = Instant.now();
	}
}
