package com.runvas.backend.course;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runvas.auth.service.JwtProvider;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// docs/api-contract.md POST /courses, GET /courses/{courseId} 예시 요청/응답과 실제 응답 모양이
// 일치하는지 확인하는 계약 테스트.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class CourseControllerContractTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
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

	private final ObjectMapper objectMapper = new ObjectMapper();

	// docs/api-contract.md POST /courses 요청 본문 예시.
	private static final String CREATE_COURSE_REQUEST_JSON = """
			{
			  "title": "Heart Run in Seoul",
			  "description": "A heart-shaped running route near the river.",
			  "path": [
			    { "latitude": 37.5665, "longitude": 126.978, "sequence": 0 },
			    { "latitude": 37.567, "longitude": 126.979, "sequence": 1 }
			  ],
			  "waypoints": [
			    { "latitude": 37.5665, "longitude": 126.978, "sequence": 0 },
			    { "latitude": 37.567, "longitude": 126.979, "sequence": 1 }
			  ],
			  "distanceMeters": 1240,
			  "estimatedDurationSeconds": 480,
			  "bounds": {
			    "southWest": { "latitude": 37.5665, "longitude": 126.978 },
			    "northEast": { "latitude": 37.567, "longitude": 126.979 }
			  },
			  "visibility": "PUBLIC",
			  "tags": ["heart", "city"]
			}
			""";

	private String createUserToken(String nickname) {
		User user = userRepository.saveAndFlush(User.createKakaoUser("kakao-" + nickname, null, nickname, null));
		return jwtProvider.createAccessToken(user.getId());
	}

	@Test
	void createReturnsDocumentedShape() throws Exception {
		String token = createUserToken("course-author-1");

		mockMvc.perform(post("/api/courses")
						.contentType(APPLICATION_JSON)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.content(CREATE_COURSE_REQUEST_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.course.id").exists())
				.andExpect(jsonPath("$.course.authorId").exists())
				.andExpect(jsonPath("$.course.title").value("Heart Run in Seoul"))
				.andExpect(jsonPath("$.course.description").value("A heart-shaped running route near the river."))
				.andExpect(jsonPath("$.course.path[0].sequence").value(0))
				.andExpect(jsonPath("$.course.waypoints[1].sequence").value(1))
				.andExpect(jsonPath("$.course.distanceMeters").value(1240))
				.andExpect(jsonPath("$.course.estimatedDurationSeconds").value(480))
				.andExpect(jsonPath("$.course.bounds.southWest.latitude").value(37.5665))
				.andExpect(jsonPath("$.course.visibility").value("PUBLIC"))
				.andExpect(jsonPath("$.course.tags", org.hamcrest.Matchers.containsInAnyOrder("heart", "city")))
				.andExpect(jsonPath("$.course.likeCount").value(0))
				.andExpect(jsonPath("$.course.likedByMe").value(false))
				.andExpect(jsonPath("$.course.bookmarkedByMe").value(false))
				.andExpect(jsonPath("$.course.createdAt").exists())
				.andExpect(jsonPath("$.course.updatedAt").exists());
	}

	@Test
	void getByIdReturnsDocumentedShapeIncludingBookmarkedByMe() throws Exception {
		String token = createUserToken("course-author-2");

		String createResponse = mockMvc.perform(post("/api/courses")
						.contentType(APPLICATION_JSON)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.content(CREATE_COURSE_REQUEST_JSON))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String courseId = objectMapper.readTree(createResponse).get("course").get("id").asText();

		mockMvc.perform(get("/api/courses/" + courseId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.course.id").value(courseId))
				.andExpect(jsonPath("$.course.likedByMe").value(false))
				.andExpect(jsonPath("$.course.bookmarkedByMe").value(false));
	}

	@Test
	void createWithoutAuthReturns401() throws Exception {
		mockMvc.perform(post("/api/courses")
						.contentType(APPLICATION_JSON)
						.content(CREATE_COURSE_REQUEST_JSON))
				.andExpect(status().isUnauthorized());
	}
}
