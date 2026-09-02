package com.runvas.backend.community;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, Block.BlockId> {

	List<Block> findByIdBlockerIdOrderByCreatedAtDesc(String blockerId);

	// PostService/CommentService/CourseCommentService의 목록 조회에서 "차단된 작성자 ID 집합"을
	// 한 번만 조회해 in-memory 필터링하는 데 쓴다.
	@Query("select b.id.blockedId from Block b where b.id.blockerId = :blockerId")
	Set<String> findBlockedIdsByBlockerId(@Param("blockerId") String blockerId);
}
