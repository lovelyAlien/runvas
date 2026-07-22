package com.runvas.backend.community;

import com.runvas.backend.admin.DailyCountProjection;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, String> {
	List<Post> findAllByOrderByCreatedAtDesc();

	// 관리자 대시보드 전용 조회 (docs/superpowers/specs/2026-07-21-admin-dashboard-design.md).
	Page<Post> findByTitleContainingIgnoreCase(String title, Pageable pageable);

	@Query("select cast(p.createdAt as date) as day, count(p) as cnt from Post p "
			+ "where p.createdAt >= :since group by cast(p.createdAt as date)")
	List<DailyCountProjection> countDailySince(@Param("since") Instant since);
}
