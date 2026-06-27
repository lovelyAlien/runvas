package com.runvas.backend.community;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, Like.LikeId> {
}
