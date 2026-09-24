package com.examprep.result.entity;

import com.examprep.common.entity.BaseEntity;
import com.examprep.result.model.ScoreBreakdowns.SectionScore;
import com.examprep.result.model.ScoreBreakdowns.TopicScore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The scored outcome of one attempt. {@code rank}/{@code percentile} are written only by
 * final ranking ({@code rankFinal = true}). Until then the API shows the live rank from
 * the Redis leaderboard.
 */
@Getter
@Setter
@Entity
@Table(name = "results")
public class Result extends BaseEntity {

    @Column(name = "attempt_id", nullable = false, unique = true, updatable = false)
    private UUID attemptId;

    @Column(name = "test_id", nullable = false, updatable = false)
    private UUID testId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "incorrect_count", nullable = false)
    private int incorrectCount;

    @Column(name = "partial_count", nullable = false)
    private int partialCount;

    @Column(name = "unattempted_count", nullable = false)
    private int unattemptedCount;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal accuracy;

    @Column(name = "time_taken_seconds", nullable = false)
    private int timeTakenSeconds;

    /** Only a student's first attempt competes for rank; re-attempts are practice. */
    @Column(name = "is_ranked", nullable = false)
    private boolean ranked;

    @Column(name = "rank")
    private Integer rank;

    @Column(precision = 6, scale = 3)
    private BigDecimal percentile;

    @Column(name = "rank_final", nullable = false)
    private boolean rankFinal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "section_scores", nullable = false, columnDefinition = "jsonb")
    private List<SectionScore> sectionScores = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topic_scores", nullable = false, columnDefinition = "jsonb")
    private List<TopicScore> topicScores = new ArrayList<>();

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;
}
