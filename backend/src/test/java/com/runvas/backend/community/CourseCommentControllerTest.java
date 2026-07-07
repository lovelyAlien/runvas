package com.runvas.backend.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runvas.auth.service.JwtProvider;
import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.GeoPoint;
import com.runvas.backend.common.RoutePoint;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class CourseCommentControllerTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@TempDir
	static Path uploadDir;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("runvas.jwt.secret", () -> "dev-secret-dev-secret-dev-secret-dev-secret");
		registry.add("runvas.jwt.expiration-seconds", () -> "3600");
		registry.add("runvas.upload.dir", () -> uploadDir.toString());
		registry.add("runvas.upload.base-url", () -> "http://localhost:8921");
	}

	@AfterAll
	static void cleanupUploadDir() throws Exception {
		if (Files.exists(uploadDir)) {
			try (var paths = Files.walk(uploadDir)) {
				paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (Exception ignored) {
						// best-effort cleanup
					}
				});
			}
		}
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	CourseRepository courseRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String createUserToken(String nickname) {
		User user = userRepository.saveAndFlush(User.createKakaoUser("kakao-" + nickname, null, nickname, null));
		return jwtProvider.createAccessToken(user.getId());
	}

	private String createCourse(String authorId, CourseVisibility visibility) {
		Course course = new Course(
				authorId,
				"테스트 코스",
				"설명",
				List.of(new RoutePoint(37.5, 127.0, 0), new RoutePoint(37.51, 127.01, 1)),
				List.of(new RoutePoint(37.5, 127.0, 0), new RoutePoint(37.51, 127.01, 1)),
				1000,
				600,
				new GeoBounds(new GeoPoint(37.5, 127.0), new GeoPoint(37.51, 127.01)),
				visibility,
				java.util.Set.of());
		return courseRepository.saveAndFlush(course).getId();
	}

	private String authorIdFromToken(String token) {
		return jwtProvider.parseUserId(token).toString();
	}

	@Test
	void createCommentOnPublicCourseSucceedsWithoutImage() throws Exception {
		String token = createUserToken("author1");
		String courseId = createCourse(authorIdFromToken(token), CourseVisibility.PUBLIC);

		mockMvc.perform(multipart("/api/courses/" + courseId + "/comments")
						.param("body", "오늘 완주했습니다!")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comment.courseId").value(courseId))
				.andExpect(jsonPath("$.comment.body").value("오늘 완주했습니다!"))
				.andExpect(jsonPath("$.comment.imageUrl").doesNotExist())
				.andExpect(jsonPath("$.comment.author.nickname").value("author1"));
	}

	@Test
	void createCommentOnPrivateCourseReturns400() throws Exception {
		String token = createUserToken("author2");
		String courseId = createCourse(authorIdFromToken(token), CourseVisibility.PRIVATE);

		mockMvc.perform(multipart("/api/courses/" + courseId + "/comments")
						.param("body", "비공개 코스 댓글 시도")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void createCommentWithInvalidImageExtensionReturns400() throws Exception {
		String token = createUserToken("author3");
		String courseId = createCourse(authorIdFromToken(token), CourseVisibility.PUBLIC);
		MockMultipartFile invalidImage =
				new MockMultipartFile("image", "photo.gif", "image/gif", "not-a-real-image".getBytes());

		mockMvc.perform(multipart("/api/courses/" + courseId + "/comments")
						.file(invalidImage)
						.param("body", "잘못된 이미지 형식")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void createCommentWithOversizedImageReturns400() throws Exception {
		String token = createUserToken("author4");
		String courseId = createCourse(authorIdFromToken(token), CourseVisibility.PUBLIC);
		byte[] oversizedContent = new byte[6 * 1024 * 1024];
		MockMultipartFile oversizedImage =
				new MockMultipartFile("image", "photo.jpg", "image/jpeg", oversizedContent);

		mockMvc.perform(multipart("/api/courses/" + courseId + "/comments")
						.file(oversizedImage)
						.param("body", "용량 초과 이미지")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isBadRequest());
	}

	@Test
	void nonAuthorCannotUpdateOrDeleteComment() throws Exception {
		String authorToken = createUserToken("author5");
		String otherToken = createUserToken("other5");
		String courseId = createCourse(authorIdFromToken(authorToken), CourseVisibility.PUBLIC);

		String commentId = createComment(courseId, authorToken, "원본 댓글");

		mockMvc.perform(multipart(HttpMethod.PATCH, "/api/courses/" + courseId + "/comments/" + commentId)
						.param("body", "수정 시도")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		mockMvc.perform(delete("/api/courses/" + courseId + "/comments/" + commentId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
	}

	@Test
	void authorCanUpdateAndDeleteOwnComment() throws Exception {
		String token = createUserToken("author6");
		String courseId = createCourse(authorIdFromToken(token), CourseVisibility.PUBLIC);
		String commentId = createComment(courseId, token, "원본 댓글");

		mockMvc.perform(multipart(HttpMethod.PATCH, "/api/courses/" + courseId + "/comments/" + commentId)
						.param("body", "수정된 댓글")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comment.body").value("수정된 댓글"));

		mockMvc.perform(delete("/api/courses/" + courseId + "/comments/" + commentId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNoContent());
	}

	@Test
	void listReturnsCommentsMatchingDocumentedShape() throws Exception {
		String token = createUserToken("author7");
		String courseId = createCourse(authorIdFromToken(token), CourseVisibility.PUBLIC);
		createComment(courseId, token, "첫 댓글");

		mockMvc.perform(get("/api/courses/" + courseId + "/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comments[0].id").exists())
				.andExpect(jsonPath("$.comments[0].courseId").value(courseId))
				.andExpect(jsonPath("$.comments[0].author.id").exists())
				.andExpect(jsonPath("$.comments[0].author.nickname").value("author7"))
				.andExpect(jsonPath("$.comments[0].body").value("첫 댓글"))
				.andExpect(jsonPath("$.comments[0].createdAt").exists())
				.andExpect(jsonPath("$.comments[0].updatedAt").exists())
				.andExpect(jsonPath("$.pageInfo.nextCursor").doesNotExist());
	}

	@Test
	void createReplySucceedsAndIncreasesParentReplyCount() throws Exception {
		String token = createUserToken("author8");
		String replierToken = createUserToken("replier8");
		String courseId = createCourse(authorIdFromToken(token), CourseVisibility.PUBLIC);
		String parentId = createComment(courseId, token, "원본 댓글");

		mockMvc.perform(multipart("/api/courses/" + courseId + "/comments")
						.param("body", "대댓글입니다")
						.param("parentCommentId", parentId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + replierToken))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comment.parentCommentId").value(parentId))
				.andExpect(jsonPath("$.comment.replyCount").value(0));

		mockMvc.perform(get("/api/courses/" + courseId + "/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comments[0].id").value(parentId))
				.andExpect(jsonPath("$.comments[0].replyCount").value(1));

		mockMvc.perform(get("/api/courses/" + courseId + "/comments/" + parentId + "/replies"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.replies[0].body").value("대댓글입니다"))
				.andExpect(jsonPath("$.replies[0].parentCommentId").value(parentId));
	}

	@Test
	void replyToReplySucceedsAndNestsCorrectly() throws Exception {
		String token = createUserToken("author9");
		String courseId = createCourse(authorIdFromToken(token), CourseVisibility.PUBLIC);
		String parentId = createComment(courseId, token, "원본 댓글");
		String replyId = createReply(courseId, token, parentId, "1차 대댓글");

		mockMvc.perform(multipart("/api/courses/" + courseId + "/comments")
						.param("body", "2차 대댓글")
						.param("parentCommentId", replyId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comment.parentCommentId").value(replyId));

		mockMvc.perform(get("/api/courses/" + courseId + "/comments/" + parentId + "/replies"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.replies[0].id").value(replyId))
				.andExpect(jsonPath("$.replies[0].replyCount").value(1));

		mockMvc.perform(get("/api/courses/" + courseId + "/comments/" + replyId + "/replies"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.replies[0].body").value("2차 대댓글"))
				.andExpect(jsonPath("$.replies[0].parentCommentId").value(replyId));
	}

	@Test
	void deletingParentCommentCascadesToNestedReplies() throws Exception {
		String token = createUserToken("author10");
		String courseId = createCourse(authorIdFromToken(token), CourseVisibility.PUBLIC);
		String parentId = createComment(courseId, token, "원본 댓글");
		String replyId = createReply(courseId, token, parentId, "1차 대댓글");
		createReply(courseId, token, replyId, "2차 대댓글");

		mockMvc.perform(delete("/api/courses/" + courseId + "/comments/" + parentId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/courses/" + courseId + "/comments/" + parentId + "/replies"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/courses/" + courseId + "/comments/" + replyId + "/replies"))
				.andExpect(status().isNotFound());
	}

	private String createReply(String courseId, String token, String parentCommentId, String body) throws Exception {
		var result = mockMvc.perform(multipart("/api/courses/" + courseId + "/comments")
						.param("body", body)
						.param("parentCommentId", parentCommentId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated())
				.andReturn();
		String json = result.getResponse().getContentAsString();
		com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
		return node.get("comment").get("id").asText();
	}

	private String createComment(String courseId, String token, String body) throws Exception {
		var result = mockMvc.perform(multipart("/api/courses/" + courseId + "/comments")
						.param("body", body)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated())
				.andReturn();
		String json = result.getResponse().getContentAsString();
		com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
		return node.get("comment").get("id").asText();
	}
}
