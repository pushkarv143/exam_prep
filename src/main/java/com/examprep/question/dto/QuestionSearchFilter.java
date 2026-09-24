package com.examprep.question.dto;

import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Query-string filters for the question bank. Every field is optional.
 *
 * @param q      case-insensitive substring match on the question text (trigram-indexed)
 * @param status defaults to "everything except ARCHIVED" when omitted
 * @param mine   only questions created by the caller
 */
public record QuestionSearchFilter(
        UUID examId,
        UUID subjectId,
        UUID chapterId,
        UUID topicId,
        UUID parentId,
        QuestionType type,
        Difficulty difficulty,
        Language language,
        QuestionStatus status,
        @Size(max = 50) String tag,
        @Size(max = 200) String q,
        Boolean mine) {
}
