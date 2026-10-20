package com.edu.portal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    public record CourseRequest(@NotBlank String title, String description) {}

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final Access access;

    public CourseController(CourseRepository courses, EnrollmentRepository enrollments,
                            UserRepository users, Access access) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.users = users;
        this.access = access;
    }

    @GetMapping
    public List<Course> all() { return courses.findAll(); }

    @GetMapping("/mine")
    public List<Course> mine(Authentication a) {
        AppUser u = access.user(a);
        return switch (u.role) {
            case ADMIN -> courses.findAll();
            case TEACHER -> courses.findByTeacherId(u.id);
            case STUDENT -> {
                List<Long> ids = enrollments.findByStudentId(u.id).stream().map(e -> e.courseId).toList();
                yield courses.findAllById(ids);
            }
        };
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Course create(@Valid @RequestBody CourseRequest r, Authentication a) {
        AppUser u = access.user(a);
        Course c = new Course();
        c.title = r.title().trim();
        c.description = r.description();
        c.teacherId = u.id;
        c.teacherName = u.name;
        return courses.save(c);
    }

    @PostMapping("/{id}/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, String> enroll(@PathVariable Long id, Authentication a) {
        AppUser u = access.user(a);
        access.course(id);
        if (enrollments.existsByCourseIdAndStudentId(id, u.id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already enrolled in this course");
        }
        Enrollment e = new Enrollment();
        e.courseId = id;
        e.studentId = u.id;
        enrollments.save(e);
        return Map.of("message", "Enrolled");
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public List<AppUser> students(@PathVariable Long id, Authentication a) {
        access.requireOwner(access.user(a), access.course(id));
        List<Long> ids = enrollments.findByCourseId(id).stream().map(e -> e.studentId).toList();
        return users.findAllById(ids);
    }
}
