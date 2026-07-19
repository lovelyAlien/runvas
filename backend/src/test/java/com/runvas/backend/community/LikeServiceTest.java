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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// LikeController를 통해 실제로 좋아요를 남긴 뒤 LikeService.unlikeAllByUser가 Course/Post의
// likeCount를 정상적으로 되돌리는지 검증한다 (AccountPurgeService가 이 메서드로 좋아요를 정리한다).
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class LikeServiceTest {

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
	PostRepository postRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	LikeService likeService;

	private User createUser(String nickname) {
		return userRepository.saveAndFlush(User.createKakaoUser("kakao-" + nickname, null, nickname, null));
	}

	private String createPost(String accessToken) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "탈퇴 좋아요 정리 테스트용 글", "body": "본문" }
								"""))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.post.id");
	}

	@Test
	void unlikeAllByUserDecrementsCourseAndPostLikeCounts() throws Exception {
		User user = createUser("purge-liker");
		String accessToken = jwtProvider.createAccessToken(user.getId());

		Course course = courseRepository.saveAndFlush(new Course(
				"author-x",
				"탈퇴 좋아요 정리 테스트 코스",
				null,
				List.of(new RoutePoint(37.0, 127.0, 0), new RoutePoint(37.001, 127.001, 1)),
				List.of(new RoutePoint(37.0, 127.0, 0), new RoutePoint(37.001, 127.001, 1)),
				200,
				120,
				new GeoBounds(new GeoPoint(37.0, 127.0), new GeoPoint(37.001, 127.001)),
				CourseVisibility.PUBLIC,
				Set.of()));
		String postId = createPost(accessToken);

		assertThat(courseRepository.findById(course.getId()).orElseThrow().getLikeCount()).isEqualTo(0);
		assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isEqualTo(0);

		mockMvc.perform(put("/api/likes/courses/" + course.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/likes/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk());

		assertThat(courseRepository.findById(course.getId()).orElseThrow().getLikeCount()).isEqualTo(1);
		assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isEqualTo(1);

		likeService.unlikeAllByUser(user.getId().toString());

		assertThat(courseRepository.findById(course.getId()).orElseThrow().getLikeCount()).isEqualTo(0);
		assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isEqualTo(0);
	}
}
