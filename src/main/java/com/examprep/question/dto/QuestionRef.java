package com.examprep.question.dto;

import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;

import java.math.BigDecimal;
import java.util.UUID;

/** Minimal, content-free view of a question that other modules (test builder, generator) need. */
public record QuestionRef(
        UUID id,
        QuestionType type,
        Difficulty difficulty,
        QuestionStatus status,
        UUID subjectId,
        UUID chapterId,
        UUID topicId,
        UUID parentId,
        BigDecimal defaultMarks,
        BigDecimal defaultNegativeMarks) {
}
