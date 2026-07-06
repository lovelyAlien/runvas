package com.runvas.backend.community;

import com.jayway.jsonpath.JsonPath;
import com.runvas.auth.service.JwtProvider;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class CommentControllerTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("runvas.jwt.secret", () -> "dev-secret-dev-secret-dev-secret-dev-secret");
		registry.add("runvas.jwt.expiration-seconds", () -> "3600");
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String createUserAndToken(String nickname) {
		User user = userRepository.saveAndFlush(User.createKakaoUser("kakao-" + nickname, null, nickname, null));
		return jwtProvider.createAccessToken(user.getId());
	}

	private String createPost(String accessToken) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "댓글 테스트용 글", "body": "본문" }
								"""))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.post.id");
	}

	private String createComment(String accessToken, String postId, String body) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/posts/" + postId + "/comments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "body": "%s" }
								""".formatted(body)))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.comment.id");
	}

	@Test
	void createCommentIncrementsPostCommentCount() throws Exception {
		String accessToken = createUserAndToken("commenter1");
		String postId = createPost(accessToken);

		mockMvc.perform(post("/api/posts/" + postId + "/comments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "body": "이 코스 저장해두고 뛰어볼게요." }
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comment.postId").value(postId))
				.andExpect(jsonPath("$.comment.author.id").isString());

		mockMvc.perform(get("/api/posts/" + postId))
				.andExpect(jsonPath("$.post.commentCount").value(1));
	}

	@Test
	void createCommentOnUnknownPostReturns404() throws Exception {
		String accessToken = createUserAndToken("commenter2");

		mockMvc.perform(post("/api/posts/unknown-post/comments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "body": "댓글" }
								"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void createCommentWithoutAuthReturns401() throws Exception {
		String accessToken = createUserAndToken("commenter3");
		String postId = createPost(accessToken);

		mockMvc.perform(post("/api/posts/" + postId + "/comments")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "body": "댓글" }
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void listReturnsCommentsInCreatedOrder() throws Exception {
		String accessToken = createUserAndToken("commenter4");
		String postId = createPost(accessToken);

		createComment(accessToken, postId, "첫 댓글");
		createComment(accessToken, postId, "두 번째 댓글");

		mockMvc.perform(get("/api/posts/" + postId + "/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comments.length()").value(2))
				.andExpect(jsonPath("$.comments[0].body").value("첫 댓글"))
				.andExpect(jsonPath("$.comments[1].body").value("두 번째 댓글"));
	}

	@Test
	void updateByNonAuthorReturns403() throws Exception {
		String ownerToken = createUserAndToken("owner3");
		String otherToken = createUserAndToken("other3");
		String postId = createPost(ownerToken);
		String commentId = createComment(ownerToken, postId, "원본 댓글");

		mockMvc.perform(patch("/api/comments/" + commentId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "body": "다른 사람이 수정 시도" }
								"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void deleteByAuthorDecrementsPostCommentCount() throws Exception {
		String accessToken = createUserAndToken("commenter5");
		String postId = createPost(accessToken);
		String commentId = createComment(accessToken, postId, "삭제될 댓글");

		mockMvc.perform(delete("/api/comments/" + commentId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/posts/" + postId))
				.andExpect(jsonPath("$.post.commentCount").value(0));
	}

	@Test
	void listRejectsNegativeLimit() throws Exception {
		String accessToken = createUserAndToken("commenter6");
		String postId = createPost(accessToken);

		mockMvc.perform(get("/api/posts/" + postId + "/comments").param("limit", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void listRejectsZeroLimit() throws Exception {
		String accessToken = createUserAndToken("commenter7");
		String postId = createPost(accessToken);

		mockMvc.perform(get("/api/posts/" + postId + "/comments").param("limit", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}
}
