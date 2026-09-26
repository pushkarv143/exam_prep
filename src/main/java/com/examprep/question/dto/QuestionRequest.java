package com.examprep.question.dto;

import com.examprep.question.entity.CognitiveLevel;
import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.entity.SourceType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.QuestionContent;
import com.examprep.question.model.QuestionTranslation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Create/replace payload for a question. Exam, subject and chapter are derived from
 * {@code topicId}. {@code marks}/{@code negativeMarks} default to the type's scheme
 * (see {@link QuestionType}). Cross-field rules (options vs answer key per type,
 * translation ids) are enforced by {@code QuestionValidator}. The workflow status is
 * not part of the payload: it changes only through the workflow endpoints.
 *
 * @param translations other languages' texts, keyed by language (the primary language is ignored)
 * @param baseVersion  the version the editor started from; a save is rejected when someone
 *                     else saved a newer version in the meantime (optional)
 * @param changeNote   short description stored with the new version (optional)
 */
public record QuestionRequest(
        @NotNull QuestionType type,
        Difficulty difficulty,
        Language language,
        @NotNull UUID topicId,
        @Size(max = 120) String subTopic,
        UUID parentId,
        @NotNull @Valid QuestionContent content,
        @Valid AnswerKey answerKey,
        @Size(max = 2) Map<Language, @Valid QuestionTranslation> translations,
        @DecimalMin("0") @DecimalMax("100") BigDecimal marks,
        @DecimalMin("0") @DecimalMax("100") BigDecimal negativeMarks,
        SourceType sourceType,
        @Size(max = 200) String source,
        @Min(1950) @Max(2100) Integer year,
        @Size(max = 60) String pyqShift,
        @Min(5) @Max(3600) Integer expectedTimeSec,
        CognitiveLevel cognitiveLevel,
        @Size(max = 10) Set<@NotBlank @Size(max = 50) String> tags,
        @Size(max = 15) Set<@NotBlank @Size(max = 80) String> concepts,
        Integer baseVersion,
        @Size(max = 500) String changeNote) {
}
