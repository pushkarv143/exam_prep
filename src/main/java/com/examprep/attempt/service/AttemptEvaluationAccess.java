package com.examprep.attempt.service;

import com.examprep.attempt.entity.AnswerState;
import com.examprep.attempt.entity.Attempt;
import com.examprep.attempt.entity.AttemptAnswer;
import com.examprep.attempt.entity.AttemptStatus;
import com.examprep.attempt.model.StudentAnswer;
import com.examprep.attempt.repository.AttemptAnswerRepository;
import com.examprep.attempt.repository.AttemptRepository;
import com.examprep.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The attempt module's API for evaluation and results. It exposes snapshots, never
 * entities, and owns every write to attempts and attempt_answers.
 */
@Service
@RequiredArgsConstructor
public class AttemptEvaluationAccess {

    private final AttemptRepository attempts;
    private final AttemptAnswerRepository answers;
    private final JdbcTemplate jdbc;

    public record AttemptSnapshot(UUID attemptId, UUID testId, UUID userId, int attemptNo, AttemptStatus status,
                                  Instant startedAt, Instant deadlineAt, Instant submittedAt, Instant evaluatedAt) {
    }

    public record AnswerRow(UUID questionId, UUID sectionId, StudentAnswer answer, AnswerState state,
                            int timeSpentSeconds, String outcome, BigDecimal marksAwarded) {
    }

    public record OutcomeRow(UUID questionId, String outcome, BigDecimal marks) {
    }

    public AttemptSnapshot get(UUID attemptId) {
        return attempts.findById(attemptId).map(AttemptEvaluationAccess::snapshot)
                .orElseThrow(() -> NotFoundException.of("Attempt", attemptId));
    }

    /** Row-locks the attempt for the caller's transaction (evaluation must be single-writer). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<AttemptSnapshot> lockForEvaluation(UUID attemptId) {
        return attempts.lockById(attemptId).map(AttemptEvaluationAccess::snapshot);
    }

    @Transactional(readOnly = true)
    public List<AnswerRow> answers(UUID attemptId) {
        return answers.findByAttemptId(attemptId).stream().map(AttemptEvaluationAccess::row).toList();
    }

    /** One JDBC batch UPDATE (a single hash partition, since attempt_id is the partition key). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveOutcomes(UUID attemptId, List<OutcomeRow> outcomes) {
        jdbc.batchUpdate("UPDATE attempt_answers SET outcome = ?, marks_awarded = ? WHERE attempt_id = ? AND question_id = ?",
                outcomes.stream().map(o -> new Object[]{o.outcome(), o.marks(), attemptId, o.questionId()}).toList());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markEvaluated(UUID attemptId, Instant at) {
        Attempt a = attempts.findById(attemptId).orElseThrow();
        a.setStatus(AttemptStatus.EVALUATED);
        a.setEvaluatedAt(at);
    }

    /** Submitted but not yet evaluated, e.g. because the evaluation message was lost. */
    @Transactional(readOnly = true)
    public List<UUID> findSubmittedBefore(Instant cutoff, int limit) {
        return jdbc.queryForList("""
                SELECT id FROM attempts WHERE status = 'SUBMITTED' AND submitted_at < ? ORDER BY submitted_at LIMIT ?
                """, UUID.class, java.sql.Timestamp.from(cutoff), limit);
    }

    private static AttemptSnapshot snapshot(Attempt a) {
        return new AttemptSnapshot(a.getId(), a.getTestId(), a.getUserId(), a.getAttemptNo(), a.getStatus(),
                a.getStartedAt(), a.getDeadlineAt(), a.getSubmittedAt(), a.getEvaluatedAt());
    }

    private static AnswerRow row(AttemptAnswer a) {
        return new AnswerRow(a.getQuestionId(), a.getSectionId(), a.getAnswer(), a.getState(), a.getTimeSpentSeconds(),
                a.getOutcome(), a.getMarksAwarded());
    }
}
