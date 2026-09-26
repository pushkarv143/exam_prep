package com.examprep.question.dto;

import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Minimal, content-free view of a question that other modules (test builder, generator) need.
 *
 * @param publishedVersion version new tests pin; null if never published
 * @param currentVersion   latest saved version (the working copy)
 */
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
        BigDecimal defaultNegativeMarks,
        Integer publishedVersion,
        int currentVersion) {

    /** Can be added to a test: published at least once and not archived. */
    public boolean usable() {
        return publishedVersion != null && status != QuestionStatus.ARCHIVED;
    }
}
