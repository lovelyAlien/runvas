package com.runvas.backend.community;

import com.jayway.jsonpath.JsonPath;
import com.runvas.auth.service.JwtProvider;
import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.GeoPoint;
import com.runvas.backend.common.RoutePoint;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.util.List;
import java.util.Set;
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
class PostControllerTest {

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
	CourseRepository courseRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String createUserAndToken(String nickname) {
		User user = userRepository.saveAndFlush(User.createKakaoUser("kakao-" + nickname, null, nickname, null));
		return jwtProvider.createAccessToken(user.getId());
	}

	private Course savePublicCourse() {
		return courseRepository.saveAndFlush(new Course(
				"author-x",
				"테스트 코스",
				null,
				List.of(new RoutePoint(37.0, 127.0, 0), new RoutePoint(37.001, 127.001, 1)),
				List.of(new RoutePoint(37.0, 127.0, 0), new RoutePoint(37.001, 127.001, 1)),
				200,
				120,
				new GeoBounds(new GeoPoint(37.0, 127.0), new GeoPoint(37.001, 127.001)),
				CourseVisibility.PUBLIC,
				Set.of()));
	}

	private Course savePrivateCourse() {
		Course course = savePublicCourse();
		course.setVisibility(CourseVisibility.PRIVATE);
		return courseRepository.saveAndFlush(course);
	}

	private String createPost(String accessToken, String title, String body) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "%s", "body": "%s" }
								""".formatted(title, body)))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.post.id");
	}

	@Test
	void createsPostAndReturnsDocumentedResponse() throws Exception {
		String accessToken = createUserAndToken("author1");

		mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "한강 하트 코스 후기",
								  "body": "초반 구간이 평탄해서 가볍게 뛰기 좋았습니다.",
								  "tags": ["hangang", "heart"]
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.post.title").value("한강 하트 코스 후기"))
				.andExpect(jsonPath("$.post.author.id").isString())
				.andExpect(jsonPath("$.post.likeCount").value(0))
				.andExpect(jsonPath("$.post.commentCount").value(0))
				.andExpect(jsonPath("$.post.likedByMe").value(false));
	}

	@Test
	void createWithoutAuthReturns401() throws Exception {
		mockMvc.perform(post("/api/posts")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "제목", "body": "본문" }
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void createWithTooLongTitleReturns400() throws Exception {
		String accessToken = createUserAndToken("author2");
		String longTitle = "a".repeat(81);

		mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "%s", "body": "본문" }
								""".formatted(longTitle)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void createWithPrivateAttachedCourseReturns404() throws Exception {
		String accessToken = createUserAndToken("author3");
		Course privateCourse = savePrivateCourse();

		mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "제목", "body": "본문", "attachedCourseId": "%s" }
								""".formatted(privateCourse.getId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void listReturnsCreatedAtDescendingAndFiltersByAttachedCourseId() throws Exception {
		String accessToken = createUserAndToken("author4");
		Course course = savePublicCourse();

		mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "코스 첨부 글", "body": "본문", "attachedCourseId": "%s" }
								""".formatted(course.getId())))
				.andExpect(status().isCreated());

		createPost(accessToken, "일반 글", "본문");

		mockMvc.perform(get("/api/posts").param("attachedCourseId", course.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.posts.length()").value(1))
				.andExpect(jsonPath("$.posts[0].title").value("코스 첨부 글"));
	}

	@Test
	void listRejectsUnsupportedSort() throws Exception {
		mockMvc.perform(get("/api/posts").param("sort", "unknownSort"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void getByIdReturnsNotFoundForUnknownPost() throws Exception {
		mockMvc.perform(get("/api/posts/unknown-post-id"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void updateByAuthorSucceeds() throws Exception {
		String accessToken = createUserAndToken("author5");
		String postId = createPost(accessToken, "원래 제목", "원래 본문");

		mockMvc.perform(patch("/api/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "수정한 제목" }
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.post.title").value("수정한 제목"))
				.andExpect(jsonPath("$.post.body").value("원래 본문"));
	}

	@Test
	void updateByNonAuthorReturns403() throws Exception {
		String ownerToken = createUserAndToken("owner1");
		String otherToken = createUserAndToken("other1");
		String postId = createPost(ownerToken, "제목", "본문");

		mockMvc.perform(patch("/api/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "다른 사람이 수정 시도" }
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
	}

	@Test
	void deleteByAuthorRemovesPost() throws Exception {
		String accessToken = createUserAndToken("author6");
		String postId = createPost(accessToken, "삭제될 글", "본문");

		mockMvc.perform(delete("/api/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/posts/" + postId))
				.andExpect(status().isNotFound());
	}

	@Test
	void deleteByNonAuthorReturns403() throws Exception {
		String ownerToken = createUserAndToken("owner2");
		String otherToken = createUserAndToken("other2");
		String postId = createPost(ownerToken, "제목", "본문");

		mockMvc.perform(delete("/api/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void listRejectsNegativeLimit() throws Exception {
		mockMvc.perform(get("/api/posts").param("limit", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void listRejectsZeroLimit() throws Exception {
		mockMvc.perform(get("/api/posts").param("limit", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void showsWithdrawnPlaceholderWhenAuthorNoLongerExists() throws Exception {
		String accessToken = createUserAndToken("author-to-withdraw");
		String postId = createPost(accessToken, "탈퇴 전 작성한 글", "본문");
		User author = userRepository.findAll().stream()
				.filter(u -> u.getNickname().equals("author-to-withdraw"))
				.findFirst()
				.orElseThrow();
		userRepository.delete(author);

		mockMvc.perform(get("/api/posts/" + postId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.post.author.nickname").value("탈퇴한 사용자"))
				.andExpect(jsonPath("$.post.author.profileImageUrl").doesNotExist())
				.andExpect(jsonPath("$.post.author.bio").doesNotExist());
	}
}
