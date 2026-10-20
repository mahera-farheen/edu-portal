package com.edu.portal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Seeds demo accounts and sample data on first start (when the database is empty). */
@Component
public class DataInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final ResourceRepository resources;
    private final AssessmentRepository assessments;
    private final GradeRepository grades;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, CourseRepository courses, EnrollmentRepository enrollments,
                           ResourceRepository resources, AssessmentRepository assessments,
                           GradeRepository grades, PasswordEncoder encoder) {
        this.users = users;
        this.courses = courses;
        this.enrollments = enrollments;
        this.resources = resources;
        this.assessments = assessments;
        this.grades = grades;
        this.encoder = encoder;
    }

    private AppUser user(String name, String email, String pw, Role role) {
        AppUser u = new AppUser();
        u.name = name; u.email = email; u.password = encoder.encode(pw); u.role = role;
        return users.save(u);
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) return;
        user("Portal Admin", "admin@edu.com", "admin123", Role.ADMIN);
        AppUser teacher = user("Meera Iyer", "teacher@edu.com", "teacher123", Role.TEACHER);
        AppUser student = user("Arjun Kumar", "student@edu.com", "student123", Role.STUDENT);
        AppUser student2 = user("Divya Nair", "student2@edu.com", "student123", Role.STUDENT);

        Course c = new Course();
        c.title = "Data Structures & Algorithms";
        c.description = "Arrays, linked lists, trees, graphs, and complexity analysis in Java.";
        c.teacherId = teacher.id; c.teacherName = teacher.name;
        c = courses.save(c);

        for (AppUser s : new AppUser[]{student, student2}) {
            Enrollment e = new Enrollment(); e.courseId = c.id; e.studentId = s.id; enrollments.save(e);
        }

        String[][] res = {
            {"Big-O Cheat Sheet", "Quick reference for time and space complexity.", "LINK", "https://www.bigocheatsheet.com/"},
            {"Java Collections Tutorial", "Official guide to the Collections Framework.", "LINK", "https://docs.oracle.com/javase/tutorial/collections/"},
        };
        for (String[] r : res) {
            LearningResource lr = new LearningResource();
            lr.title = r[0]; lr.description = r[1]; lr.type = r[2]; lr.url = r[3];
            lr.courseId = c.id; lr.uploadedById = teacher.id; lr.createdAt = LocalDateTime.now();
            resources.save(lr);
        }

        Assessment quiz = new Assessment();
        quiz.courseId = c.id; quiz.title = "Quiz 1: Arrays & Lists"; quiz.type = "QUIZ";
        quiz.maxScore = 20; quiz.date = LocalDate.now().minusDays(10);
        quiz = assessments.save(quiz);

        double[] scores = {17, 14};
        AppUser[] sts = {student, student2};
        for (int i = 0; i < 2; i++) {
            Grade g = new Grade();
            g.assessmentId = quiz.id; g.studentId = sts[i].id; g.score = scores[i];
            g.feedback = i == 0 ? "Great work on complexity analysis." : "Review linked list traversal.";
            grades.save(g);
        }
    }
}
