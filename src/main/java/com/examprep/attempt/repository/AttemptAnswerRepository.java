package com.examprep.attempt.repository;

import com.examprep.attempt.entity.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, AttemptAnswer.Key> {

    /** Prunes to a single hash partition (attempt_id is the partition key). */
    List<AttemptAnswer> findByAttemptId(UUID attemptId);

    /** Rows of [state, count] for an attempt. */
    @Query("select a.state, count(a) from AttemptAnswer a where a.attemptId = :attemptId group by a.state")
    List<Object[]> countByState(@Param("attemptId") UUID attemptId);
}
