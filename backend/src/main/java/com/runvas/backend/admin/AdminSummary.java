package com.runvas.backend.admin;

public record AdminSummary(
        long totalUsers,
        long publicCourses,
        long privateCourses,
        long totalPosts,
        long totalComments) {
}
