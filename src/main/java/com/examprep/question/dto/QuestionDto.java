package com.examprep.question.dto;

import com.examprep.catalog.dto.TopicPath;
import com.examprep.question.entity.CognitiveLevel;
import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.entity.SourceType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.QuestionContent;
import com.examprep.question.model.QuestionTranslation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full question for staff (the content studio). It includes the answer key, the solution,
 * the workflow state and what the caller may do next.
 *
 * @param missingTranslations per translated language, the texts not translated yet
 * @param actions             workflow actions the caller may perform now (see {@code QuestionAction})
 */
public record QuestionDto(
        UUID id,
        QuestionType type,
        Difficulty difficulty,
        Language language,
        TopicPath topic,
        String subTopic,
        UUID parentId,
        QuestionContent content,
        AnswerKey answerKey,
        Map<Language, QuestionTranslation> translations,
        Map<Language, List<String>> missingTranslations,
        BigDecimal marks,
        BigDecimal negativeMarks,
        QuestionStatus status,
        SourceType sourceType,
        String source,
        Short year,
        String pyqShift,
        Integer expectedTimeSec,
        CognitiveLevel cognitiveLevel,
        List<String> tags,
        List<String> concepts,
        int currentVersion,
        Integer publishedVersion,
        Instant publishedAt,
        ReviewState review,
        int openComments,
        long usedInPublishedTests,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        Instant updatedAt,
        List<String> actions) {

    /** Review assignment of a question that is (or was last) in review. */
    public record ReviewState(UUID reviewerId, String reviewerName, UUID submittedBy, String submittedByName,
                              Instant requestedAt, Instant dueAt, boolean overdue) {
    }
}
