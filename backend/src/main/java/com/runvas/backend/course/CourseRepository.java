package com.runvas.backend.course;

import com.runvas.backend.admin.DailyCountProjection;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, String> {

	// MVP 범위: bounds 겹침 + visibility=PUBLIC만 필터링하고 커서/태그/검색어는 서비스 레이어에서
	// 메모리상으로 추가 필터링한다 (실데이터 규모가 커지면 쿼리로 옮긴다 — design.md 참고).
	@Query(
			"select c from Course c where c.visibility = 'PUBLIC' "
					+ "and c.swLat <= :neLat and c.neLat >= :swLat "
					+ "and c.swLng <= :neLng and c.neLng >= :swLng "
					+ "order by c.createdAt desc")
	java.util.List<Course> findPublicCoursesWithinBounds(
			double swLat, double swLng, double neLat, double neLng);

	// bounds 없이 제목 부분 일치 검색 — 코스 이름 검색 기능용
	@Query("select c from Course c where c.visibility = 'PUBLIC' and lower(c.title) like lower(concat('%', :q, '%')) order by c.createdAt desc")
	java.util.List<Course> findPublicCoursesByTitle(@org.springframework.data.repository.query.Param("q") String q);

	// bounds 없이 태그 정확 일치 검색 (대소문자 구분 없음) — 태그 검색 기능용
	@Query("select distinct c from Course c join c.tags t where c.visibility = 'PUBLIC' and lower(t) = lower(:tag) order by c.createdAt desc")
	java.util.List<Course> findPublicCoursesByTag(@org.springframework.data.repository.query.Param("tag") String tag);

	// 본인이 만든 코스 목록 — visibility 필터 없이 PRIVATE도 포함한다.
	java.util.List<Course> findByAuthorIdOrderByCreatedAtDesc(String authorId);

	// 아래부터는 관리자 대시보드 전용 조회 (docs/superpowers/specs/2026-07-21-admin-dashboard-design.md).
	long countByVisibility(CourseVisibility visibility);

	Page<Course> findByTitleContainingIgnoreCase(String title, Pageable pageable);

	Page<Course> findByTitleContainingIgnoreCaseAndVisibility(
			String title, CourseVisibility visibility, Pageable pageable);

	@Query("select cast(c.createdAt as date) as day, count(c) as cnt from Course c "
			+ "where c.createdAt >= :since group by cast(c.createdAt as date)")
	java.util.List<DailyCountProjection> countDailySince(@Param("since") Instant since);
}
