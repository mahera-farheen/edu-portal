package com.edu.portal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ResourceController {
    public record ResourceRequest(@NotBlank String title, String description, @NotNull Long courseId,
                                  @NotBlank String type, @NotBlank String url) {}

    private final ResourceRepository resources;
    private final Access access;
    private final Path uploadDir;

    public ResourceController(ResourceRepository resources, Access access,
                              @Value("${app.upload-dir:uploads}") String dir) throws IOException {
        this.resources = resources;
        this.access = access;
        this.uploadDir = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    @GetMapping("/resources")
    public List<LearningResource> list(@RequestParam(required = false) Long courseId,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) String type) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        return resources.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> courseId == null || courseId.equals(r.courseId))
                .filter(r -> type == null || type.isBlank() || type.equalsIgnoreCase(r.type))
                .filter(r -> needle.isEmpty()
                        || (r.title != null && r.title.toLowerCase().contains(needle))
                        || (r.description != null && r.description.toLowerCase().contains(needle)))
                .toList();
    }

    @PostMapping("/resources")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public LearningResource create(@Valid @RequestBody ResourceRequest r, Authentication a) {
        return save(r.title(), r.description(), r.courseId(), r.type(), r.url(), a);
    }

    @PostMapping("/resources/upload")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public LearningResource upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam String title,
                                   @RequestParam(required = false) String description,
                                   @RequestParam Long courseId,
                                   @RequestParam(defaultValue = "PDF") String type,
                                   Authentication a) throws IOException {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        String orig = file.getOriginalFilename();
        String ext = (orig != null && orig.contains(".")) ? orig.substring(orig.lastIndexOf('.')).replaceAll("[^A-Za-z0-9.]", "") : "";
        String stored = UUID.randomUUID() + ext;
        access.requireOwner(access.user(a), access.course(courseId));
        file.transferTo(uploadDir.resolve(stored));
        return save(title, description, courseId, type, "/api/files/" + stored, a);
    }

    private LearningResource save(String title, String description, Long courseId, String type,
                                  String url, Authentication a) {
        if (!url.matches("(?i)^(https?://|/api/files/).+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link must start with http:// or https://");
        }
        AppUser u = access.user(a);
        access.requireOwner(u, access.course(courseId));
        LearningResource r = new LearningResource();
        r.title = title.trim();
        r.description = description;
        r.courseId = courseId;
        r.type = type.toUpperCase();
        r.url = url;
        r.uploadedById = u.id;
        r.createdAt = LocalDateTime.now();
        return resources.save(r);
    }

    @DeleteMapping("/resources/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Map<String, String> delete(@PathVariable Long id, Authentication a) {
        LearningResource r = resources.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        access.requireOwner(access.user(a), access.course(r.courseId));
        resources.delete(r);
        return Map.of("message", "Deleted");
    }

    /** Serves uploaded files. File names are random UUIDs, so links are unguessable. */
    @GetMapping("/files/{name:.+}")
    public ResponseEntity<Resource> file(@PathVariable String name) throws MalformedURLException {
        Path p = uploadDir.resolve(name).normalize();
        if (!p.startsWith(uploadDir) || !Files.isReadable(p)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
        Resource res = new UrlResource(p.toUri());
        MediaType mt = MediaTypeFactory.getMediaType(res).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mt).body(res);
    }
}
