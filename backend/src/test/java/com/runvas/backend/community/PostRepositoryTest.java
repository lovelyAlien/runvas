package com.runvas.backend.community;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostRepositoryTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	PostRepository postRepository;

	@Autowired
	CommentRepository commentRepository;

	@Test
	void savesAndFindsPostWithTags() {
		Post post = new Post("author-1", "한강 하트 코스 후기", "평탄해서 좋았습니다", null, Set.of("hangang", "heart"));
		postRepository.saveAndFlush(post);

		List<Post> found = postRepository.findAllByOrderByCreatedAtDesc();

		assertThat(found).hasSize(1);
		assertThat(found.get(0).getTitle()).isEqualTo("한강 하트 코스 후기");
		assertThat(found.get(0).getTags()).containsExactlyInAnyOrder("hangang", "heart");
		assertThat(found.get(0).getLikeCount()).isEqualTo(0);
		assertThat(found.get(0).getCommentCount()).isEqualTo(0);
	}

	@Test
	void savesAndFindsCommentsByPostIdInCreatedOrder() {
		Post post = postRepository.saveAndFlush(new Post("author-1", "제목", "본문", null, Set.of()));

		commentRepository.saveAndFlush(new Comment(post.getId(), "author-2", "첫 댓글"));
		commentRepository.saveAndFlush(new Comment(post.getId(), "author-3", "두 번째 댓글"));

		List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId());

		assertThat(comments).hasSize(2);
		assertThat(comments.get(0).getBody()).isEqualTo("첫 댓글");
		assertThat(comments.get(1).getBody()).isEqualTo("두 번째 댓글");
	}

	@Test
	void deletingPostCascadesComments() {
		Post post = postRepository.saveAndFlush(new Post("author-1", "제목", "본문", null, Set.of()));
		commentRepository.saveAndFlush(new Comment(post.getId(), "author-2", "댓글"));

		postRepository.delete(post);
		postRepository.flush();

		assertThat(commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId())).isEmpty();
	}
}
