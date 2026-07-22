package com.runvas.backend.admin;

import com.runvas.backend.community.Post;
import com.runvas.backend.community.PostRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AdminPostControllerTest {

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
    PostRepository postRepository;

    @Test
    @WithMockUser(username = "operator", roles = "ADMIN")
    void searchFiltersByTitle() throws Exception {
        postRepository.saveAndFlush(new Post("author-1", "한강 러닝 후기", "본문", null, Set.of()));
        postRepository.saveAndFlush(new Post("author-1", "남산 등산 후기", "본문", null, Set.of()));

        mockMvc.perform(get("/admin/posts").param("q", "한강"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("한강 러닝 후기")))
                .andExpect(content().string(not(containsString("남산 등산 후기"))));
    }

    @Test
    @WithMockUser(username = "operator", roles = "ADMIN")
    void searchWithNegativePageDoesNotFail() throws Exception {
        mockMvc.perform(get("/admin/posts").param("page", "-1"))
                .andExpect(status().isOk());
    }

    @Test
    void listRedirectsWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/posts"))
                .andExpect(status().is3xxRedirection());
    }
}
