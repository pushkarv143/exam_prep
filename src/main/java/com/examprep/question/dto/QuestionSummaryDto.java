package com.examprep.question.dto;

import com.examprep.catalog.dto.TopicPath;
import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lightweight row for question-bank tables and the test builder's picker. */
public record QuestionSummaryDto(
        UUID id,
        QuestionType type,
        Difficulty difficulty,
        Language language,
        TopicPath topic,
        String textPreview,
        BigDecimal marks,
        BigDecimal negativeMarks,
        QuestionStatus status,
        List<String> tags,
        Instant createdAt) {
}
