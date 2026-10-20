package com.edu.portal;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Small helper for looking up the current user / courses and enforcing ownership. */
@Component
public class Access {
    private final UserRepository users;
    private final CourseRepository courses;

    public Access(UserRepository users, CourseRepository courses) {
        this.users = users;
        this.courses = courses;
    }

    public AppUser user(Authentication a) {
        return users.findByEmail(a.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    public Course course(Long id) {
        return courses.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }

    public void requireOwner(AppUser u, Course c) {
        if (u.role != Role.ADMIN && !u.id.equals(c.teacherId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not manage this course");
        }
    }
}
