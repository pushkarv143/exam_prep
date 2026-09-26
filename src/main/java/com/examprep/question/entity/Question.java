package com.examprep.question.entity;

import com.examprep.common.entity.BaseEntity;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.QuestionContent;
import com.examprep.question.model.QuestionTranslations;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A question in the bank: its <b>working copy</b> (the latest saved version) plus workflow
 * and review state. Every save also writes an immutable {@code question_versions} row;
 * tests pin one of those versions, so later edits never change a paper students took.
 *
 * <p>{@code examId}, {@code subjectId} and {@code chapterId} are denormalised from
 * {@code topicId}. They are set by the service from the catalog, so filters and
 * subject-wise analytics never need joins across the catalog.
 */
@Getter
@Setter
@Entity
@Table(name = "questions")
public class Question extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty = Difficulty.MEDIUM;

    /** Primary language of {@link #content}. Other languages live in {@link #translations}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language = Language.EN;

    @Column(name = "exam_id")
    private UUID examId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "topic_id")
    private UUID topicId;

    @Column(name = "sub_topic", length = 120)
    private String subTopic;

    /** Set for questions that belong to a PARAGRAPH (shared passage) parent. */
    @Column(name = "parent_id")
    private UUID parentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private QuestionContent content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correct_answer", nullable = false, columnDefinition = "jsonb")
    private AnswerKey answerKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private QuestionTranslations translations = QuestionTranslations.NONE;

    @Column(name = "default_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal defaultMarks;

    @Column(name = "default_negative_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal defaultNegativeMarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status = QuestionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 12)
    private SourceType sourceType;

    /** Free text: exam name for a PYQ, institute or book title, author. */
    private String source;

    private Short year;

    /** PYQ shift, e.g. "27 Jan 2024, Shift 1". */
    @Column(name = "pyq_shift", length = 60)
    private String pyqShift;

    @Column(name = "expected_time_sec")
    private Short expectedTimeSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "cognitive_level", length = 10)
    private CognitiveLevel cognitiveLevel;

    @ElementCollection
    @CollectionTable(name = "question_tags", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "tag", nullable = false)
    private Set<String> tags = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "question_concepts", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "concept", nullable = false)
    private Set<String> concepts = new HashSet<>();

    // ------------------------------------------------------------------ versions

    /** Number of the latest saved version (the working copy). */
    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    /** Version new tests use; null until the question is first published. */
    @Column(name = "published_version")
    private Integer publishedVersion;

    @Column(name = "published_at")
    private Instant publishedAt;

    // ------------------------------------------------------------------ review

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "review_requested_at")
    private Instant reviewRequestedAt;

    @Column(name = "review_due_at")
    private Instant reviewDueAt;

    @Column(name = "review_overdue_sent", nullable = false)
    private boolean reviewOverdueSent;

    /** Can be added to tests: some version is published and the question is not archived. */
    public boolean isUsable() {
        return publishedVersion != null && status != QuestionStatus.ARCHIVED;
    }

    /** The working copy differs from the live version (a revision is in progress). */
    public boolean hasUnpublishedRevision() {
        return publishedVersion != null && publishedVersion != currentVersion;
    }

    public void clearReview() {
        reviewerId = null;
        submittedBy = null;
        reviewRequestedAt = null;
        reviewDueAt = null;
        reviewOverdueSent = false;
    }
}
