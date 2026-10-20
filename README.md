# Learning Hub – Education Resource & Performance Management System

A full-stack portal where teachers publish learning resources and record student results, and students find materials and track their performance.

**Stack:** Java 17 · Spring Boot 3.2 (Web, Security, Data JPA, Validation) · H2 database · vanilla HTML/CSS/JS frontend (served by Spring Boot, no build step).

## Features
- Role-based access: **Admin**, **Teacher**, **Student** (HTTP Basic auth, BCrypt passwords, method-level `@PreAuthorize`)
- Courses with enrollment
- Resource library: add links or upload files, search and filter by course/type
- Performance management: assessments, per-student grades with feedback, averages
- Dashboards per role with grade bars
- Admin user management; students can self-register

## Run
Requires JDK 17+ and Maven 3.9+.
```bash
mvn spring-boot:run
```
Open http://localhost:8080

| Role    | Email             | Password   |
|---------|-------------------|------------|
| Admin   | admin@edu.com     | admin123   |
| Teacher | teacher@edu.com   | teacher123 |
| Student | student@edu.com   | student123 |

Demo data is created on first start. Data is stored in `./data` (H2 file DB) and uploads in `./uploads`; both are git-ignored. H2 console: `/h2-console` (JDBC URL `jdbc:h2:file:./data/edudb`, user `sa`, empty password).

Docker: `docker build -t edu-portal . && docker run -p 8080:8080 edu-portal`

## REST API (all under `/api`, Basic auth)
| Method & path | Who | Purpose |
|---|---|---|
| POST `/auth/register` | public | Create student account |
| GET `/auth/me` | any | Current user |
| GET `/courses`, `/courses/mine` | any | List courses |
| POST `/courses` | teacher/admin | Create course |
| POST `/courses/{id}/enroll` | student | Enroll |
| GET `/courses/{id}/students` | teacher/admin | Enrolled students |
| GET `/resources?courseId=&type=&q=` | any | Search resources |
| POST `/resources`, `/resources/upload` | teacher/admin | Add link / upload file |
| DELETE `/resources/{id}` | teacher/admin | Remove resource |
| POST `/assessments`, GET `/courses/{id}/assessments` | teacher/admin, any | Assessments |
| POST `/grades` | teacher/admin | Create/update a grade |
| GET `/grades/me`, `/courses/{id}/grades` | student, teacher/admin | Results |
| GET `/dashboard` | any | Role-specific stats |
| GET/POST `/admin/users` | admin | Manage users |

## Production notes
Swap H2 for PostgreSQL/MySQL (change `spring.datasource.*` and add the driver), replace Basic auth with JWT/sessions + HTTPS, and change the seeded passwords.
