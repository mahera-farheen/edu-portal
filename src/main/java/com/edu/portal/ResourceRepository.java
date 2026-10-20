package com.edu.portal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<LearningResource, Long> {
    List<LearningResource> findAllByOrderByCreatedAtDesc();
}
