package com.runvas.user.controller;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
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
}
