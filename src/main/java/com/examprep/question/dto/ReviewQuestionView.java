package com.examprep.question.dto;

import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.QuestionContent.MatchItem;
import com.examprep.question.model.QuestionContent.Media;
import com.examprep.question.model.QuestionContent.Option;
import com.examprep.question.model.QuestionContent.Solution;
import com.examprep.question.model.QuestionTranslation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Post-test solution review: the question <em>with</em> its answer key and solution.
 * Only served once the result module decides solutions are released.
 */
public record ReviewQuestionView(UUID id, QuestionType type, UUID parentId, String text, List<Media> images,
                                 List<Option> options, List<MatchItem> matchLeft, List<MatchItem> matchRight,
                                 AnswerKey answerKey, Solution solution, Language language,
                                 Map<Language, QuestionTranslation> translations) {
}
