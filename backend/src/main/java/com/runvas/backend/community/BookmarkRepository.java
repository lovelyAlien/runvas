package com.runvas.backend.community;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Bookmark.BookmarkId> {

	List<Bookmark> findByIdUserIdOrderByCreatedAtDesc(String userId);

	void deleteAllByIdUserId(String userId);
}
