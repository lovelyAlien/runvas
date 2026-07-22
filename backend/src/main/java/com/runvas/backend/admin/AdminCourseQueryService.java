package com.runvas.backend.admin;

import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AdminCourseQueryService {

    private final CourseRepository courseRepository;

    public AdminCourseQueryService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    public Page<Course> search(String q, CourseVisibility visibility, int page, int size) {
        String keyword = q == null ? "" : q;
        PageRequest pageRequest = PageRequest.of(page, size);
        if (visibility == null) {
            return courseRepository.findByTitleContainingIgnoreCase(keyword, pageRequest);
        }
        return courseRepository.findByTitleContainingIgnoreCaseAndVisibility(keyword, visibility, pageRequest);
    }
}
