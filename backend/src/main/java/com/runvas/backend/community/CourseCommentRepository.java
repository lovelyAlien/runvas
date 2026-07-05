package com.runvas.backend.community;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseCommentRepository extends JpaRepository<CourseComment, String> {

	// createdAt desc, id desc 기준 keyset pagination의 첫 페이지. 대댓글은 제외(최상위 댓글만).
	@Query(
			"select c from CourseComment c where c.courseId = :courseId "
					+ "and c.parentCommentId is null order by c.createdAt desc, c.id desc")
	List<CourseComment> findFirstPage(@Param("courseId") String courseId, Pageable pageable);

	// cursor로 넘어온 댓글보다 이전(더 오래된) 최상위 댓글만 조회한다.
	@Query(
			"select c from CourseComment c where c.courseId = :courseId "
					+ "and c.parentCommentId is null "
					+ "and (c.createdAt < :cursorCreatedAt "
					+ "or (c.createdAt = :cursorCreatedAt and c.id < :cursorId)) "
					+ "order by c.createdAt desc, c.id desc")
	List<CourseComment> findNextPage(
			@Param("courseId") String courseId,
			@Param("cursorCreatedAt") Instant cursorCreatedAt,
			@Param("cursorId") String cursorId,
			Pageable pageable);

	// 특정 최상위 댓글에 달린 대댓글을 오래된 순으로 조회한다.
	List<CourseComment> findByParentCommentIdOrderByCreatedAtAsc(String parentCommentId, Pageable pageable);

	// 여러 최상위 댓글의 대댓글 수를 한 번에 집계한다(N+1 방지).
	@Query(
			"select c.parentCommentId as parentCommentId, count(c) as replyCount "
					+ "from CourseComment c where c.parentCommentId in :parentCommentIds "
					+ "group by c.parentCommentId")
	List<ReplyCountRow> countRepliesByParentCommentIds(@Param("parentCommentIds") List<String> parentCommentIds);

	interface ReplyCountRow {
		String getParentCommentId();

		long getReplyCount();
	}
}
