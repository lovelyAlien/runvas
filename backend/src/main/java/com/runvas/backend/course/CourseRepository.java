package com.runvas.backend.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

	// 본인이 만든 코스 목록 — visibility 필터 없이 PRIVATE도 포함한다.
	java.util.List<Course> findByAuthorIdOrderByCreatedAtDesc(String authorId);
}
