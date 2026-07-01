package com.runvas.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.runvas.auth.service.KakaoAuthClient;
import com.runvas.auth.service.KakaoUserInfo;
import com.runvas.auth.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @MockBean
    KakaoAuthClient kakaoAuthClient;

    @MockBean
    TokenBlacklistService tokenBlacklistService;

    @Test
    void kakaoLoginCreatesUserAndReturnsDocumentedResponse() throws Exception {
        when(kakaoAuthClient.fetchUserInfo("authorization-code", "runvas://auth/kakao"))
                .thenReturn(new KakaoUserInfo("kakao-123", "runner@example.com", "Seoul Runner", null));

        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "authorizationCode": "authorization-code",
                                  "redirectUri": "runvas://auth/kakao"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.id").isString())
                .andExpect(jsonPath("$.user.email").value("runner@example.com"))
                .andExpect(jsonPath("$.user.provider").value("KAKAO"))
                .andExpect(jsonPath("$.user.nickname").value("Seoul Runner"))
                .andExpect(jsonPath("$.user.providerUserId").doesNotExist())
                .andExpect(jsonPath("$.isNewUser").value(true));
    }

    @Test
    void kakaoLoginRejectsUnsupportedProvider() throws Exception {
        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "APPLE",
                                  "authorizationCode": "authorization-code",
                                  "redirectUri": "runvas://auth/kakao"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isEmpty());
    }

    @Test
    void kakaoLoginAllowsMissingAuthorizationHeader() throws Exception {
        when(kakaoAuthClient.fetchUserInfo("authorization-code-no-header", "runvas://auth/kakao"))
                .thenReturn(new KakaoUserInfo("kakao-no-header", null, null, null));

        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "authorizationCode": "authorization-code-no-header",
                                  "redirectUri": "runvas://auth/kakao"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(nullValue()))
                .andExpect(jsonPath("$.user.nickname").value("Runvas Runner"));
    }

    @Test
    void logoutInvalidatesTokenReturningNoContent() throws Exception {
        when(kakaoAuthClient.fetchUserInfo("authorization-code-logout", "runvas://auth/kakao"))
                .thenReturn(new KakaoUserInfo("kakao-logout", "runner@example.com", "Seoul Runner", null));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "authorizationCode": "authorization-code-logout",
                                  "redirectUri": "runvas://auth/kakao"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        verify(tokenBlacklistService).blacklist(accessToken);
    }

    @Test
    void blacklistedTokenIsRejectedOnSubsequentRequest() throws Exception {
        when(kakaoAuthClient.fetchUserInfo("authorization-code-blacklisted", "runvas://auth/kakao"))
                .thenReturn(new KakaoUserInfo("kakao-blacklisted", "runner@example.com", "Seoul Runner", null));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "authorizationCode": "authorization-code-blacklisted",
                                  "redirectUri": "runvas://auth/kakao"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
        when(tokenBlacklistService.isBlacklisted(accessToken)).thenReturn(true);

        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
