package com.examprep.catalog.repository;

import com.examprep.catalog.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    List<Subject> findByExamIdOrderByDisplayOrderAscNameAsc(UUID examId);

    Optional<Subject> findByExamIdAndCode(UUID examId, String code);

    boolean existsByExamIdAndCode(UUID examId, String code);
}
