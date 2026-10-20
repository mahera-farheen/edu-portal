package com.edu.portal;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class LearningResource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String title;
    @Column(length = 2000)
    public String description;
    /** PDF, VIDEO, LINK, NOTES, SLIDES */
    public String type;
    @Column(length = 1000)
    public String url;
    public Long courseId;
    public Long uploadedById;
    public LocalDateTime createdAt;
}
