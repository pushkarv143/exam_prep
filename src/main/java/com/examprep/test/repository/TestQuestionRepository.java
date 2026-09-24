package com.examprep.test.repository;

import com.examprep.test.entity.TestQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TestQuestionRepository extends JpaRepository<TestQuestion, UUID> {

    List<TestQuestion> findByTestIdOrderByDisplayOrderAsc(UUID testId);

    List<TestQuestion> findBySectionIdOrderByDisplayOrderAsc(UUID sectionId);

    Optional<TestQuestion> findByIdAndTestId(UUID id, UUID testId);

    @Query("select tq.questionId from TestQuestion tq where tq.testId = :testId")
    Set<UUID> findQuestionIdsByTestId(@Param("testId") UUID testId);

    @Query("select distinct tq.testId from TestQuestion tq where tq.questionId = :questionId")
    List<UUID> findTestIdsByQuestionId(@Param("questionId") UUID questionId);

    @Query("select coalesce(max(tq.displayOrder), 0) from TestQuestion tq where tq.sectionId = :sectionId")
    int maxDisplayOrder(@Param("sectionId") UUID sectionId);

    @Modifying
    @Query("delete from TestQuestion tq where tq.sectionId = :sectionId")
    void deleteBySectionId(@Param("sectionId") UUID sectionId);
}
