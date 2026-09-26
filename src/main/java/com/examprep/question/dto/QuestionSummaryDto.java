package com.examprep.question.dto;

import com.examprep.catalog.dto.TopicPath;
import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.entity.SourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight row for question-bank tables, review queues and the test builder's picker.
 *
 * @param languages        the primary language first, then translated languages
 * @param publishedVersion the live version (null if never published)
 */
public record QuestionSummaryDto(
        UUID id,
        QuestionType type,
        Difficulty difficulty,
        Language language,
        List<Language> languages,
        TopicPath topic,
        String subTopic,
        String textPreview,
        BigDecimal marks,
        BigDecimal negativeMarks,
        QuestionStatus status,
        int currentVersion,
        Integer publishedVersion,
        UUID reviewerId,
        String reviewerName,
        Instant reviewDueAt,
        SourceType sourceType,
        Short year,
        List<String> tags,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
