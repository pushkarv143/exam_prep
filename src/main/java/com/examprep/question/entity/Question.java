package com.examprep.question.entity;

import com.examprep.common.entity.BaseEntity;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.QuestionContent;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A question in the bank. {@code examId}, {@code subjectId} and {@code chapterId} are
 * denormalised from {@code topicId}. They are set by the service from the catalog, so
 * filters and subject-wise analytics never need joins across the catalog.
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

    /** Set for questions that belong to a PARAGRAPH (shared passage) parent. */
    @Column(name = "parent_id")
    private UUID parentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private QuestionContent content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correct_answer", nullable = false, columnDefinition = "jsonb")
    private AnswerKey answerKey;

    @Column(name = "default_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal defaultMarks;

    @Column(name = "default_negative_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal defaultNegativeMarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status = QuestionStatus.ACTIVE;

    private String source;

    private Short year;

    @ElementCollection
    @CollectionTable(name = "question_tags", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "tag", nullable = false)
    private Set<String> tags = new HashSet<>();
}
