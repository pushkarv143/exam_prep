package com.examprep.question.dto;

import com.examprep.question.entity.CognitiveLevel;
import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.entity.SourceType;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Query-string filters for the question bank and review queues. Every field is optional.
 *
 * @param q          case-insensitive substring match on the question text (trigram-indexed)
 * @param status     workflow status; defaults to "everything except ARCHIVED" when omitted
 * @param live       only questions tests can use (a published version exists, not archived)
 * @param mine       only questions created by the caller
 * @param reviewer   "me", "none" (unassigned) or a user id; implies status IN_REVIEW unless status is given
 * @param overdue    only reviews past their SLA
 * @param translated only questions that have a translation in this language
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
        Boolean live,
        @Size(max = 50) String tag,
        @Size(max = 80) String concept,
        SourceType sourceType,
        CognitiveLevel cognitiveLevel,
        Integer year,
        Language translated,
        @Size(max = 200) String q,
        Boolean mine,
        @Size(max = 40) String reviewer,
        Boolean overdue) {
}
