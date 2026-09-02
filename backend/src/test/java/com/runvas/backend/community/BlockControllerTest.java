package com.runvas.backend.community;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class BlockControllerTest {

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

	private String userIdPathValue(String accessToken) {
		return "user_" + jwtProvider.parseUserId(accessToken);
	}

	@Test
	void blockingUserReturns201AndBlockedUserProfile() throws Exception {
		String blockerToken = createUserAndToken("blocker-a");
		String targetToken = createUserAndToken("target-a");
		String targetUserId = userIdPathValue(targetToken);

		mockMvc.perform(post("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.blockedUser.id").value(targetUserId))
				.andExpect(jsonPath("$.blockedUser.nickname").value("target-a"))
				.andExpect(jsonPath("$.createdAt").exists());
	}

	@Test
	void blockingSameUserTwiceIsIdempotent() throws Exception {
		String blockerToken = createUserAndToken("blocker-b");
		String targetToken = createUserAndToken("target-b");
		String targetUserId = userIdPathValue(targetToken);

		mockMvc.perform(post("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isOk());
	}

	@Test
	void blockingSelfReturns400() throws Exception {
		String token = createUserAndToken("self-blocker");
		String ownUserId = userIdPathValue(token);

		mockMvc.perform(post("/api/blocks/" + ownUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void blockingUnknownUserReturns404() throws Exception {
		String token = createUserAndToken("blocker-c");

		mockMvc.perform(post("/api/blocks/user_00000000-0000-0000-0000-000000000000")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void blockingWithoutAuthReturns401() throws Exception {
		mockMvc.perform(post("/api/blocks/user_00000000-0000-0000-0000-000000000000"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unblockingRemovesBlockAndIsIdempotent() throws Exception {
		String blockerToken = createUserAndToken("blocker-d");
		String targetToken = createUserAndToken("target-d");
		String targetUserId = userIdPathValue(targetToken);

		mockMvc.perform(post("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated());

		mockMvc.perform(delete("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/blocks")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocks.length()").value(0));

		mockMvc.perform(delete("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isNoContent());
	}

	@Test
	void listReturnsBlockedUsers() throws Exception {
		String blockerToken = createUserAndToken("blocker-e");
		String targetToken = createUserAndToken("target-e");
		String targetUserId = userIdPathValue(targetToken);

		mockMvc.perform(post("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/blocks")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocks[?(@.blockedUser.id == '" + targetUserId + "')]").exists())
				.andExpect(jsonPath("$.pageInfo.nextCursor").doesNotExist());
	}

	@Test
	void listWithoutAuthReturns401() throws Exception {
		mockMvc.perform(get("/api/blocks"))
				.andExpect(status().isUnauthorized());
	}
}
