package com.edu.portal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    List<Grade> findByStudentId(Long studentId);
    List<Grade> findByAssessmentIdIn(Collection<Long> assessmentIds);
    Optional<Grade> findByAssessmentIdAndStudentId(Long assessmentId, Long studentId);
}
