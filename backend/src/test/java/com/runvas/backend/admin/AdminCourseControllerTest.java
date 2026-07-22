package com.runvas.backend.admin;

import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.GeoPoint;
import com.runvas.backend.common.RoutePoint;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import java.util.List;
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
class AdminCourseControllerTest {

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
    CourseRepository courseRepository;

    private Course course(String title, CourseVisibility visibility) {
        RoutePoint point = new RoutePoint(37.5665, 126.978, 0);
        GeoBounds bounds = new GeoBounds(new GeoPoint(37.5665, 126.978), new GeoPoint(37.567, 126.979));
        return new Course(
                "author-1", title, null, List.of(point), List.of(point),
                1000, 600, bounds, visibility, Set.of());
    }

    @Test
    @WithMockUser(username = "operator", roles = "ADMIN")
    void visibilityFilterOnlyShowsMatchingCourses() throws Exception {
        courseRepository.saveAndFlush(course("공개 코스", CourseVisibility.PUBLIC));
        courseRepository.saveAndFlush(course("비공개 코스", CourseVisibility.PRIVATE));

        mockMvc.perform(get("/admin/courses").param("visibility", "PUBLIC"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("공개 코스")))
                .andExpect(content().string(not(containsString("비공개 코스"))));
    }

    @Test
    void listRedirectsWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/courses"))
                .andExpect(status().is3xxRedirection());
    }
}
