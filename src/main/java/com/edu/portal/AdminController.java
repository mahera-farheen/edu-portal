package com.edu.portal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    public record CreateUserRequest(@NotBlank String name, @NotBlank @Email String email,
                                    @NotBlank @Size(min = 6) String password, @NotNull Role role) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping("/users")
    public List<AppUser> list() { return users.findAll(); }

    @PostMapping("/users")
    public AppUser create(@Valid @RequestBody CreateUserRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AppUser u = new AppUser();
        u.name = r.name().trim();
        u.email = email;
        u.password = encoder.encode(r.password());
        u.role = r.role();
        return users.save(u);
    }
}
