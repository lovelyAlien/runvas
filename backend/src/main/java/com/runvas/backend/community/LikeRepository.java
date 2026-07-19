package com.runvas.backend.community;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, Like.LikeId> {

	List<Like> findAllByIdUserId(String userId);

	void deleteAllByIdUserId(String userId);
}
