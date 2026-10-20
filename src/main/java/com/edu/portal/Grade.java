package com.edu.portal;

import jakarta.persistence.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"assessmentId", "studentId"}))
public class Grade {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long assessmentId;
    public Long studentId;
    public double score;
    @Column(length = 1000)
    public String feedback;
}
