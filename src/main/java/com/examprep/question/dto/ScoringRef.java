package com.examprep.question.dto;

import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;

import java.util.UUID;

/** What evaluation needs from the bank: the answer key plus the catalog tags used for topic analytics. */
public record ScoringRef(UUID id, QuestionType type, AnswerKey answerKey, Difficulty difficulty, UUID subjectId,
                         UUID chapterId, UUID topicId) {
}
