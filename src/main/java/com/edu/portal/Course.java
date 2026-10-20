package com.edu.portal;

import jakarta.persistence.*;

@Entity
public class Course {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String title;
    @Column(length = 2000)
    public String description;
    public Long teacherId;
    public String teacherName;
}
