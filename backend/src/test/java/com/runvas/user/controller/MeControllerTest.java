package com.runvas.user.controller;

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

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
// 여러 테스트가 같은 닉네임("Seoul Runner")을 쓰므로 테스트 간 데이터가 남으면
// 닉네임 중복 검사 때문에 실행 순서에 따라 실패할 수 있어 트랜잭션으로 롤백한다.
@Transactional
class MeControllerTest {

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

    @Test
    void returnsCurrentUser() throws Exception {
        User user = userRepository.saveAndFlush(User.createKakaoUser(
                "kakao-123",
                "runner@example.com",
                "Seoul Runner",
                null
        ));
        String accessToken = jwtProvider.createAccessToken(user.getId());

        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("user_" + user.getId()))
                .andExpect(jsonPath("$.user.email").value("runner@example.com"))
                .andExpect(jsonPath("$.user.provider").value("KAKAO"))
                .andExpect(jsonPath("$.user.providerUserId").doesNotExist());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawMarksAccountDeletedAndBlacklistsToken() throws Exception {
        User user = userRepository.saveAndFlush(User.createKakaoUser(
                "kakao-withdraw", "runner@example.com", "Seoul Runner", null
        ));
        String accessToken = jwtProvider.createAccessToken(user.getId());

        mockMvc.perform(delete("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "NOT_USING" }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawWithOtherReasonRequiresDetail() throws Exception {
        User user = userRepository.saveAndFlush(User.createKakaoUser(
                "kakao-withdraw-other", "runner@example.com", "Seoul Runner", null
        ));
        String accessToken = jwtProvider.createAccessToken(user.getId());

        mockMvc.perform(delete("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "OTHER" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void withdrawWithoutAuthReturns401() throws Exception {
        mockMvc.perform(delete("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "NOT_USING" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMeWithNicknameAlreadyUsedByAnotherUserReturns409() throws Exception {
        userRepository.saveAndFlush(User.createKakaoUser(
                "kakao-nickname-owner", "owner@example.com", "Seoul Runner", null
        ));
        User requester = userRepository.saveAndFlush(User.createKakaoUser(
                "kakao-nickname-requester", "requester@example.com", "Busan Runner", null
        ));
        String accessToken = jwtProvider.createAccessToken(requester.getId());

        mockMvc.perform(patch("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nickname": "Seoul Runner" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void updateMeWithSameNicknameAsCurrentSucceeds() throws Exception {
        User user = userRepository.saveAndFlush(User.createKakaoUser(
                "kakao-nickname-self", "self@example.com", "Seoul Runner", null
        ));
        String accessToken = jwtProvider.createAccessToken(user.getId());

        mockMvc.perform(patch("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nickname": "Seoul Runner" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.nickname").value("Seoul Runner"));
    }
}
