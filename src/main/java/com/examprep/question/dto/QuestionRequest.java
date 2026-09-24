package com.examprep.question.dto;

import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.QuestionContent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Create/replace payload for a question. Exam, subject and chapter are derived from
 * {@code topicId}. {@code marks}/{@code negativeMarks} default to the type's scheme
 * (see {@link QuestionType}). Cross-field rules (options vs answer key per type) are
 * enforced by {@code QuestionValidator}.
 */
public record QuestionRequest(
        @NotNull QuestionType type,
        Difficulty difficulty,
        Language language,
        @NotNull UUID topicId,
        UUID parentId,
        @NotNull @Valid QuestionContent content,
        @Valid AnswerKey answerKey,
        @DecimalMin("0") @DecimalMax("100") BigDecimal marks,
        @DecimalMin("0") @DecimalMax("100") BigDecimal negativeMarks,
        QuestionStatus status,
        @Size(max = 200) String source,
        @Min(1950) @Max(2100) Integer year,
        @Size(max = 10) Set<@NotBlank @Size(max = 50) String> tags) {
}
