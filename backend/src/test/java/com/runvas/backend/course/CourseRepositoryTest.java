package com.runvas.backend.course;

import com.runvas.backend.admin.DailyCountProjection;
import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.GeoPoint;
import com.runvas.backend.common.RoutePoint;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CourseRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

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
    void countByVisibilityCountsOnlyMatchingCourses() {
        courseRepository.saveAndFlush(course("공개 코스", CourseVisibility.PUBLIC));
        courseRepository.saveAndFlush(course("비공개 코스", CourseVisibility.PRIVATE));

        assertThat(courseRepository.countByVisibility(CourseVisibility.PUBLIC)).isEqualTo(1L);
        assertThat(courseRepository.countByVisibility(CourseVisibility.PRIVATE)).isEqualTo(1L);
    }

    @Test
    void searchByTitleAndVisibilityFiltersBoth() {
        courseRepository.saveAndFlush(course("한강 코스", CourseVisibility.PUBLIC));
        courseRepository.saveAndFlush(course("한강 비공개 코스", CourseVisibility.PRIVATE));

        Page<Course> found = courseRepository.findByTitleContainingIgnoreCaseAndVisibility(
                "한강", CourseVisibility.PUBLIC, PageRequest.of(0, 20));

        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().get(0).getTitle()).isEqualTo("한강 코스");
    }

    @Test
    void countDailySinceGroupsCoursesByCreationDate() {
        courseRepository.saveAndFlush(course("코스A", CourseVisibility.PUBLIC));
        courseRepository.saveAndFlush(course("코스B", CourseVisibility.PUBLIC));

        List<DailyCountProjection> counts = courseRepository.countDailySince(Instant.now().minusSeconds(3600));

        assertThat(counts).hasSize(1);
        assertThat(counts.get(0).getCnt()).isEqualTo(2L);
    }
}
