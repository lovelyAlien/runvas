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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AdminStatsService {

    private static final int TREND_DAYS = 30;

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CourseCommentRepository courseCommentRepository;

    public AdminStatsService(
            UserRepository userRepository,
            CourseRepository courseRepository,
            PostRepository postRepository,
            CommentRepository commentRepository,
            CourseCommentRepository courseCommentRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.courseCommentRepository = courseCommentRepository;
    }

    public AdminSummary summary() {
        long totalUsers = userRepository.count();
        long publicCourses = courseRepository.countByVisibility(CourseVisibility.PUBLIC);
        long privateCourses = courseRepository.countByVisibility(CourseVisibility.PRIVATE);
        long totalPosts = postRepository.count();
        long totalComments = commentRepository.count() + courseCommentRepository.count();
        return new AdminSummary(totalUsers, publicCourses, privateCourses, totalPosts, totalComments);
    }

    public List<DailyTrendPoint> userSignupTrend() {
        return fillDailyTrend(userRepository.countDailySince(since()));
    }

    public List<DailyTrendPoint> courseCreationTrend() {
        return fillDailyTrend(courseRepository.countDailySince(since()));
    }

    public List<DailyTrendPoint> postCreationTrend() {
        return fillDailyTrend(postRepository.countDailySince(since()));
    }

    private Instant since() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(TREND_DAYS - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private List<DailyTrendPoint> fillDailyTrend(List<DailyCountProjection> rows) {
        Map<LocalDate, Long> byDay = rows.stream()
                .collect(Collectors.toMap(DailyCountProjection::getDay, DailyCountProjection::getCnt));
        LocalDate start = LocalDate.now(ZoneOffset.UTC).minusDays(TREND_DAYS - 1L);
        List<DailyTrendPoint> points = new ArrayList<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = start.plusDays(i);
            points.add(new DailyTrendPoint(day, byDay.getOrDefault(day, 0L)));
        }
        return points;
    }
}
