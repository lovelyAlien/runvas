package com.runvas.backend.admin;

import com.runvas.backend.community.CommentRepository;
import com.runvas.backend.community.CourseCommentRepository;
import com.runvas.backend.community.PostRepository;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminStatsServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final CourseCommentRepository courseCommentRepository = mock(CourseCommentRepository.class);
    private final AdminStatsService adminStatsService = new AdminStatsService(
            userRepository, courseRepository, postRepository, commentRepository, courseCommentRepository);

    @Test
    void summaryCombinesCountsFromAllRepositories() {
        when(userRepository.count()).thenReturn(10L);
        when(courseRepository.countByVisibility(CourseVisibility.PUBLIC)).thenReturn(4L);
        when(courseRepository.countByVisibility(CourseVisibility.PRIVATE)).thenReturn(6L);
        when(postRepository.count()).thenReturn(3L);
        when(commentRepository.count()).thenReturn(2L);
        when(courseCommentRepository.count()).thenReturn(5L);

        AdminSummary summary = adminStatsService.summary();

        assertThat(summary.totalUsers()).isEqualTo(10L);
        assertThat(summary.publicCourses()).isEqualTo(4L);
        assertThat(summary.privateCourses()).isEqualTo(6L);
        assertThat(summary.totalPosts()).isEqualTo(3L);
        assertThat(summary.totalComments()).isEqualTo(7L);
    }

    @Test
    void userSignupTrendFillsMissingDaysWithZeroAndCoversThirtyDays() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(userRepository.countDailySince(any(Instant.class)))
                .thenReturn(List.of(projection(today, 3L)));

        List<DailyTrendPoint> trend = adminStatsService.userSignupTrend();

        assertThat(trend).hasSize(30);
        assertThat(trend.get(29).day()).isEqualTo(today);
        assertThat(trend.get(29).count()).isEqualTo(3L);
        assertThat(trend.get(0).count()).isEqualTo(0L);
    }

    private DailyCountProjection projection(LocalDate day, long cnt) {
        return new DailyCountProjection() {
            @Override
            public LocalDate getDay() {
                return day;
            }

            @Override
            public long getCnt() {
                return cnt;
            }
        };
    }
}
