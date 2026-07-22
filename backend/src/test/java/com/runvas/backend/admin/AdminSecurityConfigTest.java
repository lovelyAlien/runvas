package com.runvas.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AdminSecurityConfigTest {

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
    AdminAccountRepository adminAccountRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void unauthenticatedRequestRedirectsToLoginPage() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void loginWithWrongPasswordRedirectsBackToLoginWithError() throws Exception {
        adminAccountRepository.saveAndFlush(new AdminAccount("operator-wrong-pw", passwordEncoder.encode("correct-password")));

        mockMvc.perform(post("/admin/login")
                        .param("username", "operator-wrong-pw")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error"));
    }

    @Test
    void loginWithCorrectCredentialsRedirectsToDashboardAndRecordsLastLoginAt() throws Exception {
        adminAccountRepository.saveAndFlush(new AdminAccount("operator-correct-pw", passwordEncoder.encode("correct-password")));

        mockMvc.perform(post("/admin/login")
                        .param("username", "operator-correct-pw")
                        .param("password", "correct-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));

        assertThat(adminAccountRepository.findByUsername("operator-correct-pw").orElseThrow().getLastLoginAt()).isNotNull();
    }
}
