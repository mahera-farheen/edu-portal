package com.edu.portal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record RegisterRequest(@NotBlank String name, @NotBlank @Email String email,
                                  @NotBlank @Size(min = 6, message = "must be at least 6 characters") String password) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Access access;

    public AuthController(UserRepository users, PasswordEncoder encoder, Access access) {
        this.users = users;
        this.encoder = encoder;
        this.access = access;
    }

    /** Public self-registration always creates a STUDENT account. */
    @PostMapping("/register")
    public AppUser register(@Valid @RequestBody RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AppUser u = new AppUser();
        u.name = r.name().trim();
        u.email = email;
        u.password = encoder.encode(r.password());
        u.role = Role.STUDENT;
        return users.save(u);
    }

    @GetMapping("/me")
    public AppUser me(Authentication a) {
        return access.user(a);
    }
}
