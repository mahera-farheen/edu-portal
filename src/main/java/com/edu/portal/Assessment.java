package com.edu.portal;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class Assessment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long courseId;
    public String title;
    /** QUIZ, ASSIGNMENT, EXAM, PROJECT */
    public String type;
    public double maxScore;
    public LocalDate date;
}
