package com.edu.portal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class PerformanceController {
    public record AssessmentRequest(@NotNull Long courseId, @NotBlank String title, @NotBlank String type,
                                    @Positive double maxScore, LocalDate date) {}
    public record GradeRequest(@NotNull Long assessmentId, @NotNull Long studentId,
                               @PositiveOrZero double score, String feedback) {}
    public record PerfRow(Long gradeId, Long courseId, String courseTitle, Long assessmentId, String assessmentTitle,
                          String type, Long studentId, String studentName, double score, double maxScore,
                          double percent, String feedback) {}

    private final AssessmentRepository assessments;
    private final GradeRepository grades;
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final UserRepository users;
    private final ResourceRepository resources;
    private final Access access;

    public PerformanceController(AssessmentRepository assessments, GradeRepository grades,
                                 EnrollmentRepository enrollments, CourseRepository courses,
                                 UserRepository users, ResourceRepository resources, Access access) {
        this.assessments = assessments;
        this.grades = grades;
        this.enrollments = enrollments;
        this.courses = courses;
        this.users = users;
        this.resources = resources;
        this.access = access;
    }

    @GetMapping("/courses/{id}/assessments")
    public List<Assessment> assessments(@PathVariable Long id) {
        return assessments.findByCourseId(id);
    }

    @PostMapping("/assessments")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Assessment createAssessment(@Valid @RequestBody AssessmentRequest r, Authentication a) {
        access.requireOwner(access.user(a), access.course(r.courseId()));
        Assessment x = new Assessment();
        x.courseId = r.courseId();
        x.title = r.title().trim();
        x.type = r.type().toUpperCase();
        x.maxScore = r.maxScore();
        x.date = r.date() == null ? LocalDate.now() : r.date();
        return assessments.save(x);
    }

    /** Create or update a student's grade for an assessment. */
    @PostMapping("/grades")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public PerfRow grade(@Valid @RequestBody GradeRequest r, Authentication a) {
        Assessment as = assessments.findById(r.assessmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment not found"));
        access.requireOwner(access.user(a), access.course(as.courseId));
        if (r.score() > as.maxScore) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Score cannot exceed " + as.maxScore);
        }
        if (!enrollments.existsByCourseIdAndStudentId(as.courseId, r.studentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student is not enrolled in this course");
        }
        Grade g = grades.findByAssessmentIdAndStudentId(r.assessmentId(), r.studentId()).orElseGet(Grade::new);
        g.assessmentId = r.assessmentId();
        g.studentId = r.studentId();
        g.score = r.score();
        g.feedback = r.feedback();
        return rows(List.of(grades.save(g))).get(0);
    }

    @GetMapping("/grades/me")
    @PreAuthorize("hasRole('STUDENT')")
    public List<PerfRow> myGrades(Authentication a) {
        return rows(grades.findByStudentId(access.user(a).id));
    }

    @GetMapping("/courses/{id}/grades")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public List<PerfRow> courseGrades(@PathVariable Long id, Authentication a) {
        access.requireOwner(access.user(a), access.course(id));
        return courseRows(id);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(Authentication a) {
        AppUser u = access.user(a);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", u.role);
        switch (u.role) {
            case STUDENT -> {
                List<PerfRow> rows = rows(grades.findByStudentId(u.id));
                out.put("enrolledCourses", enrollments.findByStudentId(u.id).size());
                out.put("gradedItems", rows.size());
                out.put("average", avg(rows));
                out.put("chart", groupAvg(rows));
                out.put("recent", rows.stream().sorted(Comparator.comparing(PerfRow::gradeId).reversed()).limit(5).toList());
            }
            case TEACHER, ADMIN -> {
                List<Course> cs = u.role == Role.ADMIN ? courses.findAll() : courses.findByTeacherId(u.id);
                List<Map<String, Object>> chart = new ArrayList<>();
                int students = 0;
                List<PerfRow> all = new ArrayList<>();
                for (Course c : cs) {
                    List<PerfRow> rows = courseRows(c.id);
                    all.addAll(rows);
                    students += enrollments.findByCourseId(c.id).size();
                    chart.add(Map.of("label", c.title, "value", avg(rows)));
                }
                out.put("courses", cs.size());
                out.put("enrollments", students);
                out.put("gradedItems", all.size());
                out.put("average", avg(all));
                out.put("chart", chart);
                if (u.role == Role.ADMIN) {
                    out.put("users", users.count());
                    out.put("resources", resources.count());
                }
            }
        }
        return out;
    }

    // ----- helpers -----
    private List<PerfRow> courseRows(Long courseId) {
        List<Long> ids = assessments.findByCourseId(courseId).stream().map(x -> x.id).toList();
        if (ids.isEmpty()) return List.of();
        return rows(grades.findByAssessmentIdIn(ids));
    }

    private double avg(List<PerfRow> rows) {
        return round(rows.stream().mapToDouble(PerfRow::percent).average().orElse(0));
    }

    private List<Map<String, Object>> groupAvg(List<PerfRow> rows) {
        Map<String, Double> m = rows.stream().collect(Collectors.groupingBy(PerfRow::courseTitle,
                LinkedHashMap::new, Collectors.averagingDouble(PerfRow::percent)));
        List<Map<String, Object>> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(Map.of("label", k, "value", round(v))));
        return out;
    }

    private static double round(double v) { return Math.round(v * 10) / 10.0; }

    private List<PerfRow> rows(List<Grade> gs) {
        Map<Long, Assessment> am = assessments.findAllById(gs.stream().map(g -> g.assessmentId).distinct().toList())
                .stream().collect(Collectors.toMap(x -> x.id, Function.identity()));
        Map<Long, Course> cm = courses.findAllById(am.values().stream().map(x -> x.courseId).distinct().toList())
                .stream().collect(Collectors.toMap(x -> x.id, Function.identity()));
        Map<Long, AppUser> um = users.findAllById(gs.stream().map(g -> g.studentId).distinct().toList())
                .stream().collect(Collectors.toMap(x -> x.id, Function.identity()));
        List<PerfRow> out = new ArrayList<>();
        for (Grade g : gs) {
            Assessment as = am.get(g.assessmentId);
            Course c = cm.get(as.courseId);
            AppUser s = um.get(g.studentId);
            double pct = as.maxScore == 0 ? 0 : round(g.score / as.maxScore * 100);
            out.add(new PerfRow(g.id, c.id, c.title, as.id, as.title, as.type, s.id, s.name,
                    g.score, as.maxScore, pct, g.feedback));
        }
        return out;
    }
}
