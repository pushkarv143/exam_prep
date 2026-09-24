package com.examprep.catalog.repository;

import com.examprep.catalog.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    Optional<Exam> findByCode(String code);

    boolean existsByCode(String code);

    List<Exam> findAllByActiveTrueOrderByDisplayOrderAscNameAsc();

    List<Exam> findAllByOrderByDisplayOrderAscNameAsc();
}
