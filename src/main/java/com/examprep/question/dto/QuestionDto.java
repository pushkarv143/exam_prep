package com.examprep.question.dto;

import com.examprep.catalog.dto.TopicPath;
import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.QuestionContent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full question for staff (question editor). It includes the answer key and the solution. */
public record QuestionDto(
        UUID id,
        QuestionType type,
        Difficulty difficulty,
        Language language,
        TopicPath topic,
        UUID parentId,
        QuestionContent content,
        AnswerKey answerKey,
        BigDecimal marks,
        BigDecimal negativeMarks,
        QuestionStatus status,
        String source,
        Short year,
        List<String> tags,
        boolean usedInPublishedTest,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
