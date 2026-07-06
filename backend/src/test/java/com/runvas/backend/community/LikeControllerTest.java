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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class LikeControllerTest {

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

	private String createPost(String accessToken) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "좋아요 테스트용 글", "body": "본문" }
								"""))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.post.id");
	}

	@Test
	void likingPostTwiceIsIdempotent() throws Exception {
		String accessToken = createUserAndToken("liker1");
		String postId = createPost(accessToken);

		mockMvc.perform(put("/api/likes/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(true))
				.andExpect(jsonPath("$.likeCount").value(1));

		mockMvc.perform(put("/api/likes/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(true))
				.andExpect(jsonPath("$.likeCount").value(1));
	}

	@Test
	void unlikingRemovesLikeAndUnlikingAgainIsIdempotent() throws Exception {
		String accessToken = createUserAndToken("liker2");
		String postId = createPost(accessToken);

		mockMvc.perform(put("/api/likes/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));

		mockMvc.perform(delete("/api/likes/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(false))
				.andExpect(jsonPath("$.likeCount").value(0));

		mockMvc.perform(delete("/api/likes/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(false))
				.andExpect(jsonPath("$.likeCount").value(0));
	}

	@Test
	void likingUnsupportedTargetTypeReturns400() throws Exception {
		String accessToken = createUserAndToken("liker3");

		mockMvc.perform(put("/api/likes/users/some-id")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void likingUnknownPostReturns404() throws Exception {
		String accessToken = createUserAndToken("liker4");

		mockMvc.perform(put("/api/likes/posts/unknown-post")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isNotFound());
	}

	@Test
	void likingWithoutAuthReturns401() throws Exception {
		mockMvc.perform(put("/api/likes/posts/some-id"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void likingCourseIncrementsCourseLikeCount() throws Exception {
		String accessToken = createUserAndToken("liker5");
		Course course = courseRepository.saveAndFlush(new Course(
				"author-x",
				"좋아요 테스트 코스",
				null,
				List.of(new RoutePoint(37.0, 127.0, 0), new RoutePoint(37.001, 127.001, 1)),
				List.of(new RoutePoint(37.0, 127.0, 0), new RoutePoint(37.001, 127.001, 1)),
				200,
				120,
				new GeoBounds(new GeoPoint(37.0, 127.0), new GeoPoint(37.001, 127.001)),
				CourseVisibility.PUBLIC,
				Set.of()));

		mockMvc.perform(put("/api/likes/courses/" + course.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetType").value("courses"))
				.andExpect(jsonPath("$.liked").value(true))
				.andExpect(jsonPath("$.likeCount").value(1));
	}
}
