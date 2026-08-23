package com.runvas.backend.community;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, String> {

	Optional<Report> findByReporterIdAndTargetTypeAndTargetIdAndStatus(
			String reporterId, ReportTargetType targetType, String targetId, ReportStatus status);

	List<Report> findAllByTargetTypeAndTargetIdAndStatus(
			ReportTargetType targetType, String targetId, ReportStatus status);

	Page<Report> findAllByStatus(ReportStatus status, Pageable pageable);

	Page<Report> findAllByStatusAndTargetType(ReportStatus status, ReportTargetType targetType, Pageable pageable);
}
