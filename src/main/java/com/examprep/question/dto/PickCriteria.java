package com.examprep.question.dto;

import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionType;

import java.util.Set;
import java.util.UUID;

/**
 * Filter for random question selection (test auto-generation). Null or empty means "any".
 *
 * @param excludeUsedInPublishedTests skip questions students may already have seen in another published test
 */
public record PickCriteria(
        UUID subjectId,
        Set<UUID> chapterIds,
        Set<UUID> topicIds,
        Difficulty difficulty,
        Set<QuestionType> types,
        Language language,
        boolean excludeUsedInPublishedTests) {
}
